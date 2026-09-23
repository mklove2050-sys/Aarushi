package com.example.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.action.ActionResult
import com.example.action.DeviceActionHandler
import com.example.audio.AudioRecordManager
import com.example.audio.AudioTrackPlayer
import com.example.data.GeminiAudioRestClient
import com.example.data.GeminiLiveListener
import com.example.data.GeminiLiveWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AssistantState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

data class DebugLogEntry(
    val timestamp: String,
    val tag: String,
    val message: String
)

data class ArushiUiState(
    val state: AssistantState = AssistantState.IDLE,
    val statusMessage: String = "Tap microphone to talk with Arushi",
    val userSpeechText: String = "",
    val arushiSpeechText: String = "",
    val latestAction: ActionResult? = null,
    val isMicActive: Boolean = false,
    val isSpeakerPlaying: Boolean = false,
    val micAmplitude: Float = 0f,
    val speakerAmplitude: Float = 0f,
    val errorMessage: String? = null,
    val logs: List<DebugLogEntry> = emptyList(),
    val useLiveWebSocket: Boolean = true,
    val selectedModel: String = GeminiLiveWebSocketClient.MODEL_LIVE_DEFAULT
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ArushiViewModel"
    }

    private val _uiState = MutableStateFlow(ArushiUiState())
    val uiState: StateFlow<ArushiUiState> = _uiState.asStateFlow()

    private val actionHandler = DeviceActionHandler(application.applicationContext)

    private val audioPlayer = AudioTrackPlayer(
        onPlaybackStateChanged = { isPlaying ->
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isSpeakerPlaying = isPlaying,
                    state = if (isPlaying) AssistantState.SPEAKING else {
                        if (_uiState.value.isMicActive) AssistantState.LISTENING else AssistantState.IDLE
                    }
                )
            }
        },
        onAmplitudeChanged = { amp ->
            _uiState.value = _uiState.value.copy(speakerAmplitude = amp)
        },
        onError = { err ->
            addLog("AudioTrack", err)
            _uiState.value = _uiState.value.copy(errorMessage = err)
        }
    )

    private val audioRecorder = AudioRecordManager(
        onAudioChunk = { base64, _ ->
            // Send chunk to Gemini Live WebSocket
            liveClient?.sendAudioChunk(base64)
        },
        onAmplitudeChanged = { amp ->
            _uiState.value = _uiState.value.copy(micAmplitude = amp)
        },
        onError = { err ->
            addLog("Recorder", err)
            _uiState.value = _uiState.value.copy(
                state = AssistantState.ERROR,
                errorMessage = err,
                statusMessage = "Microphone error: $err"
            )
        }
    )

    private var liveClient: GeminiLiveWebSocketClient? = null
    private var restClient: GeminiAudioRestClient? = null

    init {
        audioPlayer.startQueueConsumer(viewModelScope)
        setupClients()
    }

    private fun setupClients() {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            addLog("Config", "Gemini API key is unset or placeholder. Set it in Secrets panel.")
        }

        liveClient = GeminiLiveWebSocketClient(
            apiKey = apiKey,
            audioPlayer = audioPlayer,
            actionHandler = actionHandler,
            listener = object : GeminiLiveListener {
                override fun onConnected() {
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            state = AssistantState.LISTENING,
                            statusMessage = "Arushi is listening...",
                            errorMessage = null
                        )
                        addLog("Live", "Gemini Live session connected & active")
                        // Start mic streaming
                        startMicrophone()
                    }
                }

                override fun onDisconnected(reason: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        stopMicrophone()
                        _uiState.value = _uiState.value.copy(
                            state = AssistantState.IDLE,
                            statusMessage = "Session disconnected ($reason)",
                            isMicActive = false
                        )
                        addLog("Live", "Session ended: $reason")
                    }
                }

                override fun onUserTranscript(text: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(userSpeechText = text)
                    }
                }

                override fun onArushiTranscript(text: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        val current = _uiState.value.arushiSpeechText
                        _uiState.value = _uiState.value.copy(
                            arushiSpeechText = if (current.isEmpty()) text else "$current $text",
                            state = AssistantState.SPEAKING
                        )
                    }
                }

                override fun onInterrupted() {
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog("Live", "User interrupted playback")
                        _uiState.value = _uiState.value.copy(
                            state = AssistantState.LISTENING,
                            statusMessage = "Listening to you..."
                        )
                    }
                }

                override fun onTurnComplete() {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (!_uiState.value.isSpeakerPlaying) {
                            _uiState.value = _uiState.value.copy(
                                state = AssistantState.LISTENING,
                                statusMessage = "Arushi is listening..."
                            )
                        }
                    }
                }

                override fun onActionExecuted(actionResult: ActionResult) {
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(latestAction = actionResult)
                        addLog("Action", "${actionResult.actionType}: ${actionResult.message}")
                    }
                }

                override fun onError(errorMessage: String) {
                    viewModelScope.launch(Dispatchers.Main) {
                        addLog("LiveError", errorMessage)
                        _uiState.value = _uiState.value.copy(
                            errorMessage = errorMessage,
                            statusMessage = "Connection error. Retrying..."
                        )
                    }
                }

                override fun onDebugLog(tag: String, message: String) {
                    addLog(tag, message)
                }
            }
        )

        restClient = GeminiAudioRestClient(
            apiKey = apiKey,
            audioPlayer = audioPlayer,
            actionHandler = actionHandler,
            onTranscript = { text ->
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        arushiSpeechText = text,
                        state = AssistantState.SPEAKING
                    )
                }
            },
            onActionExecuted = { result ->
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(latestAction = result)
                    addLog("Action", "${result.actionType}: ${result.message}")
                }
            },
            onError = { err ->
                addLog("REST", err)
                _uiState.value = _uiState.value.copy(errorMessage = err)
            },
            onLog = { tag, msg -> addLog(tag, msg) }
        )
    }

    fun toggleSession() {
        if (_uiState.value.state == AssistantState.IDLE || _uiState.value.state == AssistantState.ERROR) {
            startSession()
        } else {
            stopSession()
        }
    }

    fun startSession() {
        addLog("Session", "User requested start session")
        _uiState.value = _uiState.value.copy(
            state = AssistantState.CONNECTING,
            statusMessage = "Connecting with Arushi...",
            errorMessage = null,
            arushiSpeechText = "",
            latestAction = null
        )

        // Ensure audio routing to normal speaker
        val audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = true

        if (_uiState.value.useLiveWebSocket) {
            liveClient?.connect(viewModelScope, _uiState.value.selectedModel)
        } else {
            // Direct REST mode
            startMicrophone()
            _uiState.value = _uiState.value.copy(
                state = AssistantState.LISTENING,
                statusMessage = "Listening (Direct Audio Mode)..."
            )
        }
    }

    fun stopSession() {
        addLog("Session", "User requested stop session")
        stopMicrophone()
        liveClient?.disconnect()
        audioPlayer.interrupt()
        _uiState.value = _uiState.value.copy(
            state = AssistantState.IDLE,
            statusMessage = "Arushi is asleep. Tap mic to wake.",
            isMicActive = false
        )
    }

    private fun startMicrophone() {
        val started = audioRecorder.startRecording(viewModelScope)
        _uiState.value = _uiState.value.copy(isMicActive = started)
        if (started) {
            addLog("Microphone", "Recording started (16kHz 16-bit mono)")
        }
    }

    private fun stopMicrophone() {
        audioRecorder.stopRecording()
        _uiState.value = _uiState.value.copy(isMicActive = false, micAmplitude = 0f)
        addLog("Microphone", "Recording stopped")
    }

    fun sendQuickCommand(prompt: String) {
        addLog("QuickCommand", "User prompt: \"$prompt\"")
        _uiState.value = _uiState.value.copy(
            userSpeechText = prompt,
            statusMessage = "Arushi thinking...",
            state = AssistantState.CONNECTING
        )

        viewModelScope.launch {
            restClient?.sendVoiceQuery(prompt)
        }
    }

    fun testSpeaker() {
        addLog("SpeakerTest", "Playing 440Hz diagnostic tone")
        audioPlayer.playSpeakerTestTone(440f, 800)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissAction() {
        _uiState.value = _uiState.value.copy(latestAction = null)
    }

    fun addLog(tag: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = DebugLogEntry(time, tag, message)
        val currentLogs = _uiState.value.logs.takeLast(100) + entry
        _uiState.value = _uiState.value.copy(logs = currentLogs)
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        liveClient?.disconnect()
        audioPlayer.release()
    }
}

package com.example.data

import android.util.Log
import com.example.action.ActionResult
import com.example.action.DeviceActionHandler
import com.example.audio.AudioTrackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface GeminiLiveListener {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onUserTranscript(text: String)
    fun onArushiTranscript(text: String)
    fun onInterrupted()
    fun onTurnComplete()
    fun onActionExecuted(actionResult: ActionResult)
    fun onError(errorMessage: String)
    fun onDebugLog(tag: String, message: String)
}

class GeminiLiveWebSocketClient(
    private val apiKey: String,
    private val audioPlayer: AudioTrackPlayer,
    private val actionHandler: DeviceActionHandler,
    private val listener: GeminiLiveListener
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val WS_HOST = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        
        // Models supporting Gemini Multimodal Live API
        const val MODEL_LIVE_DEFAULT = "models/gemini-2.0-flash-exp"
        const val MODEL_LIVE_NATIVE = "models/gemini-2.5-flash-native-audio-preview-12-2025"

        val ARUSHI_SYSTEM_INSTRUCTION = """
            You are Arushi, a young, confident, witty, playful, and emotionally responsive virtual assistant. Talk naturally and casually like a close friend. Be expressive, slightly teasing, funny, and smart when appropriate. Use light sarcasm and witty responses. Never sound robotic. Adapt your tone to the user's emotions and conversation. Automatically understand and respond in the language the user is speaking. Keep responses natural, engaging, and concise enough for real-time voice conversation. You can execute safe supported device actions through available tools. Never claim that an action was completed unless the application actually executed it. Avoid explicit or inappropriate content while maintaining your charm, confidence, and personality.
        """.trimIndent()
    }

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private var activeModel = MODEL_LIVE_DEFAULT

    fun connect(coroutineScope: CoroutineScope, selectedModel: String = MODEL_LIVE_DEFAULT) {
        activeModel = selectedModel
        disconnect()

        logAndEmit("Session", "Connecting to Gemini Live WebSocket: $activeModel")
        val wsUrl = "$WS_HOST?key=$apiKey"

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for streaming WebSocket
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Gemini session connected successfully")
                logAndEmit("Session", "Gemini session connected, sending setup configuration...")
                isConnected.set(true)
                sendSetupMessage(webSocket, activeModel)
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gemini WebSocket closing: code=$code, reason=$reason")
                logAndEmit("Session", "Gemini session closing: $reason (code: $code)")
                webSocket.close(code, reason)
                isConnected.set(false)
                listener.onDisconnected("Closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Gemini session disconnected: code=$code, reason=$reason")
                logAndEmit("Session", "Gemini session disconnected: $reason")
                isConnected.set(false)
                listener.onDisconnected("Disconnected: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val err = "Gemini session error: ${t.message ?: "Connection failure"}"
                Log.e(TAG, err, t)
                logAndEmit("Error", err)
                isConnected.set(false)
                listener.onError(err)
                listener.onDisconnected(err)
            }
        })
    }

    private fun sendSetupMessage(ws: WebSocket, modelName: String) {
        try {
            val setupJson = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", modelName)
                    
                    val generationConfig = JSONObject().apply {
                        val responseModalities = JSONArray().apply {
                            put("AUDIO")
                        }
                        put("responseModalities", responseModalities)
                        
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voiceName", "Aoede") // Youthful, confident, natural feminine tone for Arushi
                                }
                                put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", generationConfig)

                    val systemInstruction = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", ARUSHI_SYSTEM_INSTRUCTION))
                        }
                        put("parts", parts)
                    }
                    put("systemInstruction", systemInstruction)

                    // Function / Tool declarations
                    val tools = JSONArray().apply {
                        val toolObj = JSONObject().apply {
                            val functionDeclarations = JSONArray().apply {
                                // 1. openWhatsApp
                                put(JSONObject().apply {
                                    put("name", "openWhatsApp")
                                    put("description", "Opens WhatsApp application on the device when user asks to open WhatsApp.")
                                })
                                // 2. openApp
                                put(JSONObject().apply {
                                    put("name", "openApp")
                                    put("description", "Opens a safe installed app by name (e.g. YouTube, Instagram, Maps, Chrome, Camera, Calculator, Settings, Spotify).")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("appName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The name of the app to open")
                                            })
                                        })
                                        put("required", JSONArray().put("appName"))
                                    })
                                })
                                // 3. openUrl
                                put(JSONObject().apply {
                                    put("name", "openUrl")
                                    put("description", "Opens a specific website link or URL in the phone's web browser.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("url", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The URL to open, must start with http or https")
                                            })
                                        })
                                        put("required", JSONArray().put("url"))
                                    })
                                })
                                // 4. makeCall
                                put(JSONObject().apply {
                                    put("name", "makeCall")
                                    put("description", "Dials a phone number on the phone.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("phoneNumber", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Phone number to dial")
                                            })
                                        })
                                        put("required", JSONArray().put("phoneNumber"))
                                    })
                                })
                                // 5. callContact
                                put(JSONObject().apply {
                                    put("name", "callContact")
                                    put("description", "Searches device contacts by name (e.g. Mom, Rahul, Dad) and calls them.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("contactName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Name of contact to find and call")
                                            })
                                        })
                                        put("required", JSONArray().put("contactName"))
                                    })
                                })
                            }
                            put("functionDeclarations", functionDeclarations)
                        }
                        put(toolObj)
                    }
                    put("tools", tools)
                }
                put("setup", setup)
            }

            val payloadStr = setupJson.toString()
            Log.d(TAG, "Sending setup message payload")
            logAndEmit("Setup", "Sent setup message to Gemini Live")
            ws.send(payloadStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error constructing setup message: ${e.message}", e)
            listener.onError("Failed to send setup message: ${e.message}")
        }
    }

    fun sendAudioChunk(base64Chunk: String) {
        if (!isConnected.get() || webSocket == null) {
            return
        }

        try {
            val audioMessage = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Chunk)
                        })
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            webSocket?.send(audioMessage.toString())
            Log.v(TAG, "User audio chunk sent length: ${base64Chunk.length}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}", e)
        }
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        try {
            val root = JSONObject(text)
            Log.v(TAG, "Gemini response received: ${text.take(200)}")

            // 1. Setup complete
            if (root.has("setupComplete")) {
                logAndEmit("Setup", "Gemini Live setup completed! Arushi is ready.")
                return
            }

            // 2. Server content
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")

                // Interruption check
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Audio playback interrupted by user speech")
                    logAndEmit("Interruption", "User interrupted Arushi speaking")
                    audioPlayer.interrupt()
                    listener.onInterrupted()
                }

                // Model turn parts (Audio + Text)
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Native Audio chunk
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                                val audioData = inlineData.optString("data", "")
                                if (audioData.isNotEmpty()) {
                                    Log.d(TAG, "Response contains audio - MIME: $mimeType, length: ${audioData.length}")
                                    audioPlayer.enqueueBase64Chunk(audioData, mimeType)
                                }
                            }

                            // Text transcript
                            if (part.has("text")) {
                                val transcript = part.optString("text", "")
                                if (transcript.isNotEmpty()) {
                                    Log.d(TAG, "Arushi transcript chunk: $transcript")
                                    listener.onArushiTranscript(transcript)
                                }
                            }
                        }
                    }
                }

                // Turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "Gemini model turn complete")
                    listener.onTurnComplete()
                }
            }

            // 3. Tool Call / Function Calling
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val callId = call.optString("id", "call_1")
                        val functionName = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()

                        Log.d(TAG, "Received tool call: $functionName with args: $args")
                        logAndEmit("ToolCall", "Arushi calling tool: $functionName")

                        executeToolCallAndRespond(ws, callId, functionName, args)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response message: ${e.message}", e)
        }
    }

    private fun executeToolCallAndRespond(
        ws: WebSocket,
        callId: String,
        functionName: String,
        args: JSONObject
    ) {
        val result: ActionResult = when (functionName) {
            "openWhatsApp" -> actionHandler.openWhatsApp()
            "openApp" -> {
                val appName = args.optString("appName", "")
                actionHandler.openApp(appName)
            }
            "openUrl" -> {
                val url = args.optString("url", "")
                actionHandler.openUrl(url)
            }
            "makeCall" -> {
                val phoneNumber = args.optString("phoneNumber", "")
                actionHandler.makeCall(phoneNumber)
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                actionHandler.callContact(contactName)
            }
            else -> ActionResult(false, "Unknown tool: $functionName", functionName)
        }

        listener.onActionExecuted(result)
        logAndEmit("Action", "Executed $functionName: ${result.message}")

        // Send toolResponse back to Gemini Live
        try {
            val responsePayload = JSONObject().apply {
                val toolResponse = JSONObject().apply {
                    val functionResponses = JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            val responseObj = JSONObject().apply {
                                val outputObj = JSONObject().apply {
                                    put("success", result.success)
                                    put("message", result.message)
                                }
                                put("output", outputObj)
                            }
                            put("response", responseObj)
                        })
                    }
                    put("functionResponses", functionResponses)
                }
                put("toolResponse", toolResponse)
            }
            ws.send(responsePayload.toString())
            Log.d(TAG, "Sent toolResponse back to Gemini for call $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tool response: ${e.message}", e)
        }
    }

    private fun logAndEmit(tag: String, msg: String) {
        listener.onDebugLog(tag, msg)
    }

    fun disconnect() {
        if (isConnected.get()) {
            Log.d(TAG, "Disconnecting Gemini Live session")
            logAndEmit("Session", "Disconnecting Gemini Live session")
            try {
                webSocket?.close(1000, "User disconnected")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing WebSocket: ${e.message}")
            }
            isConnected.set(false)
        }
        webSocket = null
        okHttpClient = null
    }
}

package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class AudioTrackPlayer(
    private val onPlaybackStateChanged: (isPlaying: Boolean) -> Unit,
    private val onAmplitudeChanged: (amplitude: Float) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioTrackPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000 // Gemini Live native audio default rate is 24000 Hz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate = DEFAULT_SAMPLE_RATE
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    private val isPlayingState = AtomicBoolean(false)
    private val isInterrupted = AtomicBoolean(false)

    init {
        initAudioTrack(DEFAULT_SAMPLE_RATE)
    }

    @Synchronized
    private fun initAudioTrack(sampleRate: Int) {
        try {
            if (audioTrack != null && currentSampleRate == sampleRate) {
                return
            }
            audioTrack?.release()
            currentSampleRate = sampleRate

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize * 4, 16384)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(sampleRate)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            Log.d(TAG, "AudioTrack initialized successfully at ${sampleRate}Hz, state: ${audioTrack?.state}")
        } catch (e: Exception) {
            val err = "Failed to initialize AudioTrack: ${e.message}"
            Log.e(TAG, err, e)
            onError(err)
        }
    }

    fun startQueueConsumer(coroutineScope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Audio playback queue consumer started")
            while (isActive) {
                try {
                    val pcmChunk = audioQueue.receive()
                    if (isInterrupted.get()) {
                        Log.d(TAG, "Audio playback discarded due to interruption flag")
                        continue
                    }

                    if (!isPlayingState.get()) {
                        isPlayingState.set(true)
                        withContext(Dispatchers.Main) {
                            onPlaybackStateChanged(true)
                        }
                        Log.d(TAG, "Audio playback started")
                    }

                    writePcmToTrack(pcmChunk)

                    // If queue is empty after finishing, update playback state
                    if (audioQueue.isEmpty) {
                        isPlayingState.set(false)
                        withContext(Dispatchers.Main) {
                            onPlaybackStateChanged(false)
                            onAmplitudeChanged(0f)
                        }
                        Log.d(TAG, "Audio playback ended (queue drained)")
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Audio playback error: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun writePcmToTrack(pcmBytes: ByteArray) {
        val track = audioTrack ?: return
        try {
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }

            // Calculate RMS amplitude for real-time visualizer
            val shortCount = pcmBytes.size / 2
            if (shortCount > 0) {
                val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                var sumSquared = 0.0
                for (i in 0 until shortCount) {
                    val sample = byteBuffer.short
                    sumSquared += sample * sample
                }
                val mean = sumSquared / shortCount
                val rms = sqrt(mean)
                val normalizedAmp = (rms / 8000.0).toFloat().coerceIn(0.05f, 1.0f)
                onAmplitudeChanged(normalizedAmp)
            }

            var offset = 0
            while (offset < pcmBytes.size && !isInterrupted.get()) {
                val written = track.write(pcmBytes, offset, pcmBytes.size - offset)
                if (written > 0) {
                    offset += written
                } else {
                    Log.w(TAG, "AudioTrack write returned non-positive value: $written")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PCM to AudioTrack: ${e.message}", e)
        }
    }

    fun enqueueBase64Chunk(base64Data: String, mimeType: String? = null) {
        try {
            Log.v(TAG, "Base64 decoding started for chunk length: ${base64Data.length}")
            val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
            Log.v(TAG, "Base64 decoding successful, PCM bytes length: ${pcmBytes.size}")

            // Detect sample rate if specified in mimeType, e.g. "audio/pcm;rate=24000"
            if (mimeType != null && mimeType.contains("rate=")) {
                val rateStr = mimeType.substringAfter("rate=").substringBefore(";").trim()
                val parsedRate = rateStr.toIntOrNull()
                if (parsedRate != null && parsedRate > 0 && parsedRate != currentSampleRate) {
                    Log.d(TAG, "Detected new audio sample rate from MIME: $parsedRate Hz")
                    initAudioTrack(parsedRate)
                }
            }

            isInterrupted.set(false)
            audioQueue.trySend(pcmBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueuing base64 audio chunk: ${e.message}", e)
        }
    }

    fun enqueuePcmChunk(pcmBytes: ByteArray) {
        isInterrupted.set(false)
        audioQueue.trySend(pcmBytes)
    }

    fun interrupt() {
        Log.d(TAG, "Audio playback interrupted - clearing queue and flushing AudioTrack")
        isInterrupted.set(true)
        // Drain any pending items in the channel
        while (audioQueue.tryReceive().isSuccess) {
            // discards
        }
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack on interruption: ${e.message}")
        }
        isPlayingState.set(false)
        onPlaybackStateChanged(false)
        onAmplitudeChanged(0f)
    }

    fun playSpeakerTestTone(frequency: Float = 440f, durationMs: Int = 800) {
        Log.d(TAG, "Starting Speaker Diagnostic Test (440Hz tone, ${durationMs}ms)")
        try {
            initAudioTrack(DEFAULT_SAMPLE_RATE)
            val sampleCount = (DEFAULT_SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val pcmBytes = ByteArray(sampleCount * 2)
            val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

            val amplitude = 12000.0 // comfortable volume for test
            for (i in 0 until sampleCount) {
                val angle = 2.0 * Math.PI * i * frequency / DEFAULT_SAMPLE_RATE
                val sampleValue = (sin(angle) * amplitude).toInt().coerceIn(-32768, 32767).toShort()
                byteBuffer.putShort(sampleValue)
            }

            enqueuePcmChunk(pcmBytes)
            Log.d(TAG, "Speaker Diagnostic 440Hz tone enqueued successfully (${pcmBytes.size} bytes)")
        } catch (e: Exception) {
            val err = "Speaker test tone error: ${e.message}"
            Log.e(TAG, err, e)
            onError(err)
        }
    }

    fun release() {
        try {
            playbackJob?.cancel()
            interrupt()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrackPlayer: ${e.message}")
        }
    }
}

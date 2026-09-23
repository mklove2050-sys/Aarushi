package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecordManager(
    private val onAudioChunk: (base64Data: String, rawPcm: ByteArray) -> Unit,
    private val onAmplitudeChanged: (amplitude: Float) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecordManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_CHUNK_SIZE = 1024 // 1024 shorts = 2048 bytes ~ 64ms at 16kHz
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun startRecording(coroutineScope: CoroutineScope): Boolean {
        if (isRecording) {
            Log.d(TAG, "Microphone already recording")
            return true
        }

        try {
            Log.d(TAG, "Microphone started - initializing AudioRecord at ${SAMPLE_RATE}Hz")
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufferSize, BUFFER_CHUNK_SIZE * 2 * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val err = "AudioRecord initialization failed (state != STATE_INITIALIZED)"
                Log.e(TAG, err)
                onError(err)
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "Microphone started recording successfully")

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(BUFFER_CHUNK_SIZE)
                val byteBuffer = ByteArray(BUFFER_CHUNK_SIZE * 2)

                while (isActive && isRecording) {
                    val readShorts = audioRecord?.read(shortBuffer, 0, BUFFER_CHUNK_SIZE) ?: -1
                    if (readShorts > 0) {
                        // Calculate RMS amplitude for visualizer
                        var sumSquared = 0.0
                        for (i in 0 until readShorts) {
                            val sample = shortBuffer[i]
                            sumSquared += sample * sample

                            // Convert 16-bit short to Little-Endian bytes
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }

                        val mean = sumSquared / readShorts
                        val rms = sqrt(mean)
                        // Normalize 0.0 to 1.0 (clamped)
                        val normalizedAmp = (rms / 8000.0).toFloat().coerceIn(0.02f, 1.0f)
                        onAmplitudeChanged(normalizedAmp)

                        val chunkBytes = byteBuffer.copyOf(readShorts * 2)
                        val base64Chunk = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)
                        Log.v(TAG, "Microphone audio chunk created length: ${chunkBytes.size}")
                        onAudioChunk(base64Chunk, chunkBytes)
                    } else if (readShorts < 0) {
                        Log.e(TAG, "AudioRecord read error code: $readShorts")
                    }
                }
            }
            return true
        } catch (e: Exception) {
            val errorMsg = "Failed to start microphone: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError(errorMsg)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        Log.d(TAG, "Microphone stopped")
        isRecording = false
        try {
            recordingJob?.cancel()
            recordingJob = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            onAmplitudeChanged(0f)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
    }
}

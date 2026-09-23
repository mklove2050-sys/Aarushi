package com.example.data

import android.util.Log
import com.example.action.ActionResult
import com.example.action.DeviceActionHandler
import com.example.audio.AudioTrackPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAudioRestClient(
    private val apiKey: String,
    private val audioPlayer: AudioTrackPlayer,
    private val actionHandler: DeviceActionHandler,
    private val onTranscript: (String) -> Unit,
    private val onActionExecuted: (ActionResult) -> Unit,
    private val onError: (String) -> Unit,
    private val onLog: (String, String) -> Unit
) {
    companion object {
        private const val TAG = "GeminiAudioRestClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-native-audio-preview-12-2025:generateContent"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun sendVoiceQuery(userText: String) = withContext(Dispatchers.IO) {
        try {
            onLog("REST", "Sending query via Gemini Native Audio REST: \"$userText\"")
            val url = "$BASE_URL?key=$apiKey"

            val requestJson = JSONObject().apply {
                // System instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", GeminiLiveWebSocketClient.ARUSHI_SYSTEM_INSTRUCTION))
                    })
                })

                // Contents
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userText))
                        })
                    })
                })

                // Generation Config with native audio
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                        put("TEXT")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })

                // Tool declarations
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "openWhatsApp")
                                put("description", "Opens WhatsApp application.")
                            })
                            put(JSONObject().apply {
                                put("name", "openApp")
                                put("description", "Opens an installed app like YouTube, Instagram, Maps, Chrome, Camera, Calculator, Settings.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("appName", JSONObject().put("type", "STRING"))
                                    })
                                    put("required", JSONArray().put("appName"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "openUrl")
                                put("description", "Opens a website URL in browser.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("url", JSONObject().put("type", "STRING"))
                                    })
                                    put("required", JSONArray().put("url"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "makeCall")
                                put("description", "Dials a phone number.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("phoneNumber", JSONObject().put("type", "STRING"))
                                    })
                                    put("required", JSONArray().put("phoneNumber"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "callContact")
                                put("description", "Calls a contact by name.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contactName", JSONObject().put("type", "STRING"))
                                    })
                                    put("required", JSONArray().put("contactName"))
                                })
                            })
                        })
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val err = "Gemini REST error HTTP ${response.code}: $responseBody"
                Log.e(TAG, err)
                onError(err)
                return@withContext
            }

            val respObj = JSONObject(responseBody)
            val candidates = respObj.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)

                    // Function Call
                    if (part.has("functionCall")) {
                        val fnCall = part.getJSONObject("functionCall")
                        val fnName = fnCall.optString("name", "")
                        val fnArgs = fnCall.optJSONObject("args") ?: JSONObject()
                        executeFunction(fnName, fnArgs)
                    }

                    // Text
                    if (part.has("text")) {
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) {
                            onTranscript(text)
                        }
                    }

                    // Audio
                    if (part.has("inlineData")) {
                        val inlineData = part.getJSONObject("inlineData")
                        val mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                        val audioData = inlineData.optString("data", "")
                        if (audioData.isNotEmpty()) {
                            onLog("Audio", "Received native audio from Gemini REST (${audioData.length} chars)")
                            audioPlayer.enqueueBase64Chunk(audioData, mimeType)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendVoiceQuery: ${e.message}", e)
            onError("Query error: ${e.message}")
        }
    }

    private fun executeFunction(name: String, args: JSONObject) {
        val result = when (name) {
            "openWhatsApp" -> actionHandler.openWhatsApp()
            "openApp" -> actionHandler.openApp(args.optString("appName", ""))
            "openUrl" -> actionHandler.openUrl(args.optString("url", ""))
            "makeCall" -> actionHandler.makeCall(args.optString("phoneNumber", ""))
            "callContact" -> actionHandler.callContact(args.optString("contactName", ""))
            else -> ActionResult(false, "Unknown action $name", name)
        }
        onActionExecuted(result)
    }
}

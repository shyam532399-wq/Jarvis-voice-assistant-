package com.jarvis.assistant.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Tap-to-record voice input. Audio is sent to Gemini for transcription and a short answer.
 * Recording is local until the user taps Stop; no audio cue is played by this class.
 */
class GeminiVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun startRecording(): Boolean {
        if (recorder != null) return true
        return try {
            val file = File(appContext.cacheDir, "jarvis_voice_input.m4a")
            if (file.exists()) file.delete()
            val newRecorder = MediaRecorder()
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioSamplingRate(16000)
            newRecorder.setAudioEncodingBitRate(64000)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            audioFile = file
            recorder = newRecorder
            true
        } catch (_: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            false
        }
    }

    fun stopAndAsk(apiKey: String, onUpdate: (status: String, answer: String?) -> Unit) {
        val activeRecorder = recorder
        recorder = null
        try {
            activeRecorder?.stop()
        } catch (_: Exception) {
            try { activeRecorder?.reset() } catch (_: Exception) {}
        } finally {
            try { activeRecorder?.release() } catch (_: Exception) {}
        }

        val file = audioFile
        audioFile = null
        if (file == null || !file.exists() || file.length() < 1000L) {
            file?.delete()
            onUpdate("Awaaz record nahi hui. Dobara try karo.", null)
            return
        }
        if (apiKey.isBlank()) {
            file.delete()
            onUpdate("Gemini API key missing hai. Setup dobara check karo.", null)
            return
        }

        onUpdate("Jarvis awaaz samajh raha hai...", null)
        try {
            val audioBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            file.delete()
            val parts = JSONArray()
                .put(JSONObject().put("text",
                    "Listen to this user's audio. They may speak Hindi or Hinglish. Understand the request and reply helpfully in short, natural Hindi/Hinglish. If the audio is unclear, ask them to repeat. Do not describe the audio; answer the user directly."))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "audio/mp4")
                    .put("data", audioBase64)))
            val body = JSONObject()
                .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                .header("x-goog-api-key", apiKey.trim())
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    mainHandler.post {
                        onUpdate("Internet connection check karo, phir dobara try karo.", null)
                    }
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.use {
                        val responseBody = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            val googleMessage = try {
                                JSONObject(responseBody).getJSONObject("error").optString("message")
                            } catch (_: Exception) { "" }
                            val message = when (it.code) {
                                400 -> "Gemini request/audio format error: ${googleMessage.ifBlank { "400 Bad Request" }}"
                                403 -> "Gemini API key ya project access reject hua: ${googleMessage.ifBlank { "403 Forbidden" }}"
                                404 -> "Gemini model/API nahi mila (404): ${googleMessage.ifBlank { "Model name ya API access check karo." }}"
                                429 -> "Gemini quota/rate limit: ${googleMessage.ifBlank { "Baad mein try karo." }}"
                                else -> "Gemini error ${it.code}: ${googleMessage.ifBlank { "Dobara try karo." }}"
                            }
                            mainHandler.post { onUpdate(message, null) }
                            return
                        }
                        val responseText = try {
                            JSONObject(responseBody)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .optString("text")
                                .trim()
                        } catch (_: Exception) { "" }
                        mainHandler.post {
                            if (responseText.isBlank()) {
                                onUpdate("Jawab nahi mila. Ek baar phir bolo.", null)
                            } else {
                                onUpdate("Jarvis ready", responseText)
                            }
                        }
                    }
                }
            })
        } catch (_: Exception) {
            file.delete()
            onUpdate("Audio process nahi ho paya. Dobara try karo.", null)
        }
    }

    fun release() {
        val active = recorder
        recorder = null
        try { active?.stop() } catch (_: Exception) {}
        try { active?.release() } catch (_: Exception) {}
        audioFile?.delete()
        audioFile = null
    }
}

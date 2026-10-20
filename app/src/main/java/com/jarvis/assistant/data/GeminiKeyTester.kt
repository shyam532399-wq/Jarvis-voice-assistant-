package com.jarvis.assistant.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

sealed class KeyTestResult {
    object Success : KeyTestResult()
    data class Failure(val message: String) : KeyTestResult()
}

object GeminiKeyTester {

    private val client = OkHttpClient()
    private const val MODEL = "gemini-1.5-flash"

    fun test(apiKey: String, onResult: (KeyTestResult) -> Unit) {
        if (apiKey.isBlank()) {
            onResult(KeyTestResult.Failure("Key is empty"))
            return
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", "Say OK")
                ))
            ))
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(KeyTestResult.Failure("No internet or request failed: ${e.message}"))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (it.isSuccessful) {
                        onResult(KeyTestResult.Success)
                    } else {
                        val code = it.code
                        val msg = when (code) {
                            400 -> "Invalid key format"
                            403 -> "Key rejected (check it's enabled for Gemini API)"
                            429 -> "Rate limited, but key looks valid"
                            else -> "Server returned $code"
                        }
                        onResult(KeyTestResult.Failure(msg))
                    }
                }
            }
        })
    }
}

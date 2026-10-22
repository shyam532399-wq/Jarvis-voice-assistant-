package com.jarvis.assistant.service

/**
 * Converts provider/network failures into user-actionable, non-secret messages.
 * Never include API keys or complete provider payloads in UI status text.
 */
internal object ReliabilityErrors {
    fun forHttp(code: Int, providerMessage: String = ""): String {
        val message = providerMessage.lowercase()
        return when {
            code == 429 || message.contains("quota") || message.contains("rate limit") ->
                "Gemini quota/rate limit khatam ya busy hai. Quota reset hone par dobara try karo."
            code == 400 -> "Request format invalid hai. App update ya request configuration check karo."
            code == 401 || code == 403 -> "API key invalid ya permission denied hai. Google AI Studio mein key aur API access check karo."
            code == 404 -> "Model/API endpoint nahi mila. Model name aur API support check karo."
            code == 408 || code == 504 -> "Server timeout hua. Network check karke dobara try karo."
            code in 500..599 -> "Gemini server temporary unavailable hai. Thodi der baad retry karo."
            else -> "Gemini request fail hui (HTTP $code). Network aur API settings check karo."
        }
    }

    fun forSocket(code: Int?, message: String = ""): String {
        val lower = message.lowercase()
        return when {
            code == 401 || code == 403 || lower.contains("api key") || lower.contains("unauthorized") ->
                "Gemini Live API key/access error. Key aur API permission check karo."
            code == 404 || lower.contains("model") && lower.contains("not found") ->
                "Gemini Live model/endpoint nahi mila. Model name aur API support check karo."
            code == 429 || lower.contains("quota") || lower.contains("rate limit") ->
                "Gemini Live quota/rate limit hit hua. Baad mein dobara try karo."
            code != null && code >= 500 -> "Gemini Live server temporary unavailable hai."
            else -> "Gemini Live connection fail hui. Internet aur API key check karo."
        }
    }
}

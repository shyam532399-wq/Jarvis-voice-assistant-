package com.jarvis.assistant.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ReliabilityErrorsTest {
    @Test fun authErrorsAreActionable() {
        assertEquals(
            "API key invalid ya permission denied hai. Google AI Studio mein key aur API access check karo.",
            ReliabilityErrors.forHttp(403)
        )
    }

    @Test fun quotaErrorsAreActionable() {
        assertEquals(
            "Gemini quota/rate limit khatam ya busy hai. Quota reset hone par dobara try karo.",
            ReliabilityErrors.forHttp(429)
        )
        assertEquals(
            "Gemini quota/rate limit khatam ya busy hai. Quota reset hone par dobara try karo.",
            ReliabilityErrors.forHttp(400, "Quota exceeded")
        )
    }

    @Test fun endpointErrorsAreActionable() {
        assertEquals(
            "Model/API endpoint nahi mila. Model name aur API support check karo.",
            ReliabilityErrors.forHttp(404)
        )
    }

    @Test fun serverErrorsAreRetryableInMessage() {
        assertEquals(
            "Gemini server temporary unavailable hai. Thodi der baad retry karo.",
            ReliabilityErrors.forHttp(503)
        )
    }

    @Test fun websocketAuthErrorDoesNotExposeSecrets() {
        assertEquals(
            "Gemini Live API key/access error. Key aur API permission check karo.",
            ReliabilityErrors.forSocket(401, "unauthorized")
        )
    }
}

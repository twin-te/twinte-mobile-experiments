package net.twinte.mobile_experiments.core.auth

import androidx.compose.runtime.Composable

interface SessionStore {
    suspend fun getSessionId(): String?

    suspend fun saveSessionId(sessionId: String)

    suspend fun clearSessionId()
}

class MemorySessionStore(
    initialSessionId: String? = null,
) : SessionStore {
    private var sessionId: String? = initialSessionId

    override suspend fun getSessionId(): String? = sessionId

    override suspend fun saveSessionId(sessionId: String) {
        this.sessionId = sessionId
    }

    override suspend fun clearSessionId() {
        sessionId = null
    }
}

@Composable
expect fun rememberSessionStore(): SessionStore

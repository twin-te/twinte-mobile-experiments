package net.twinte.mobile_experiments.core.auth

import androidx.compose.runtime.Composable

interface SessionStore {
    suspend fun getSessionId(): String?

    suspend fun saveSessionId(sessionId: String)

    suspend fun clearSessionId()
}

interface SecureKeyValueStore {
    suspend fun getString(key: String): String?

    suspend fun putString(key: String, value: String)

    suspend fun remove(key: String)
}

class SecureKeyValueStoreSessionStore(
    private val keyValueStore: SecureKeyValueStore,
) : SessionStore {
    override suspend fun getSessionId(): String? =
        keyValueStore.getString(SessionIdKey)

    override suspend fun saveSessionId(sessionId: String) {
        keyValueStore.putString(SessionIdKey, sessionId)
    }

    override suspend fun clearSessionId() {
        keyValueStore.remove(SessionIdKey)
    }

    private companion object {
        const val SessionIdKey = "session_id"
    }
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

package net.twinte.mobile_experiments.core.auth

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
        keyValueStore.getString(SESSION_ID_KEY)

    override suspend fun saveSessionId(sessionId: String) {
        keyValueStore.putString(SESSION_ID_KEY, sessionId)
    }

    override suspend fun clearSessionId() {
        keyValueStore.remove(SESSION_ID_KEY)
    }

    private companion object {
        const val SESSION_ID_KEY = "session_id"
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

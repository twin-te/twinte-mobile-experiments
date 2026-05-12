package net.twinte.mobile_experiments.core.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberSessionStore(): SessionStore =
    remember {
        IosSessionStore(NSUserDefaults.standardUserDefaults)
    }

private class IosSessionStore(
    private val userDefaults: NSUserDefaults,
) : SessionStore {
    override suspend fun getSessionId(): String? =
        userDefaults.stringForKey(SessionIdKey)

    override suspend fun saveSessionId(sessionId: String) {
        userDefaults.setObject(sessionId, SessionIdKey)
    }

    override suspend fun clearSessionId() {
        userDefaults.removeObjectForKey(SessionIdKey)
    }

    private companion object {
        const val SessionIdKey = "net.twinte.mobile_experiments.session_id"
    }
}

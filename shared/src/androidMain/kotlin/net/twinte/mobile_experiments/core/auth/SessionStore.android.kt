package net.twinte.mobile_experiments.core.auth

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidSessionStore(context)
    }
}

private class AndroidSessionStore(
    context: Context,
) : SessionStore {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override suspend fun getSessionId(): String? =
        preferences.getString(SessionIdKey, null)

    override suspend fun saveSessionId(sessionId: String) {
        preferences.edit()
            .putString(SessionIdKey, sessionId)
            .apply()
    }

    override suspend fun clearSessionId() {
        preferences.edit()
            .remove(SessionIdKey)
            .apply()
    }

    private companion object {
        const val PreferencesName = "twinte_auth"
        const val SessionIdKey = "session_id"
    }
}

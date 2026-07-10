package net.twinte.mobile_experiments.core.auth

import io.ktor.http.HttpStatusCode
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

class AuthRepository(
    private val sessionStore: SessionStore,
    private val authApi: AuthApi,
    private val googleSessionApi: GoogleSessionApi,
    private val appleSessionApi: AppleSessionApi,
) {
    suspend fun restoreSession(): AuthSession? {
        val session = sessionStore.getSession() ?: return null
        return runCatching {
            AuthSession(session.sessionId, authApi.getMe(session))
        }.onFailure { error ->
            if (error is TwinteApiException && error.status == HttpStatusCode.Unauthorized) {
                sessionStore.clearSessionId()
            }
        }.getOrThrow()
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthSession {
        val session = googleSessionApi.createSessionWithIdToken(idToken, sessionStore.getSession())
        sessionStore.saveSessionId(session.sessionId)
        return AuthSession(session.sessionId, authApi.getMe(session))
    }

    suspend fun signInWithAppleCredential(credential: AppleSignInCredential): AuthSession {
        val session = appleSessionApi.createSessionWithCredential(credential, sessionStore.getSession())
        sessionStore.saveSessionId(session.sessionId)
        return AuthSession(session.sessionId, authApi.getMe(session))
    }

    suspend fun getMe(): User {
        return authApi.getMe(requireSession())
    }

    suspend fun signOut() {
        val session = sessionStore.getSession()
        try {
            session?.let { authApi.logout(it) }
        } finally {
            sessionStore.clearSessionId()
        }
    }

    suspend fun deleteUserAuthentication(provider: AuthProvider): AuthSession {
        val session = requireSession()
        authApi.deleteUserAuthentication(session, provider)
        return AuthSession(session.sessionId, authApi.getMe(session))
    }

    suspend fun deleteAccount() {
        val session = requireSession()
        try {
            authApi.deleteAccount(session)
        } finally {
            sessionStore.clearSessionId()
        }
    }

    private suspend fun requireSession(): TwinteSession =
        requireNotNull(sessionStore.getSession()) { "No session" }
}

data class AuthSession(
    val sessionId: String,
    val user: User,
)

private suspend fun SessionStore.getSession(): TwinteSession? =
    getSessionId()?.let(::TwinteSession)

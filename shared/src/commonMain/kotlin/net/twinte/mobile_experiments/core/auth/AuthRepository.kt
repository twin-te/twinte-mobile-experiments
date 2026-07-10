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
            if (error.isUnauthorized()) {
                sessionStore.clearSessionId()
            }
        }.getOrElse { error ->
            if (error.isUnauthorized()) return null
            throw error
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthSession {
        val session = googleSessionApi.createSessionWithIdToken(idToken, sessionStore.getSession())
        sessionStore.saveSessionId(session.sessionId)
        return fetchAuthSession(session)
    }

    suspend fun signInWithAppleCredential(credential: AppleSignInCredential): AuthSession {
        val session = appleSessionApi.createSessionWithCredential(credential, sessionStore.getSession())
        sessionStore.saveSessionId(session.sessionId)
        return fetchAuthSession(session)
    }

    suspend fun getMe(): User {
        return withAuthenticatedSession { session ->
            authApi.getMe(session)
        }
    }

    suspend fun signOut() {
        val session = sessionStore.getSession()
        try {
            session?.let {
                runCatching {
                    authApi.logout(it)
                }
            }
        } finally {
            sessionStore.clearSessionId()
        }
    }

    suspend fun deleteUserAuthentication(provider: AuthProvider): AuthSession {
        return withAuthenticatedSession { session ->
            authApi.deleteUserAuthentication(session, provider)
            AuthSession(session.sessionId, authApi.getMe(session))
        }
    }

    suspend fun deleteAccount() {
        withAuthenticatedSession { session ->
            authApi.deleteAccount(session)
        }
        sessionStore.clearSessionId()
    }

    private suspend fun requireSession(): TwinteSession =
        requireNotNull(sessionStore.getSession()) { "No session" }

    private suspend fun fetchAuthSession(session: TwinteSession): AuthSession =
        runCatching {
            AuthSession(session.sessionId, authApi.getMe(session))
        }.onFailure { error ->
            if (error.isUnauthorized()) {
                sessionStore.clearSessionId()
            }
        }.getOrThrow()

    private suspend fun <T> withAuthenticatedSession(block: suspend (TwinteSession) -> T): T {
        val session = requireSession()
        return runCatching {
            block(session)
        }.onFailure { error ->
            if (error.isUnauthorized()) {
                sessionStore.clearSessionId()
            }
        }.getOrThrow()
    }
}

data class AuthSession(
    val sessionId: String,
    val user: User,
)

private suspend fun SessionStore.getSession(): TwinteSession? =
    getSessionId()?.let(::TwinteSession)

private fun Throwable.isUnauthorized(): Boolean =
    this is TwinteApiException && status == HttpStatusCode.Unauthorized

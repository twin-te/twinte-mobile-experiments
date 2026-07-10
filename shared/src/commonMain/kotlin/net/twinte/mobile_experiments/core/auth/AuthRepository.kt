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

    suspend fun createGoogleAuthChallenge(): AuthChallenge = googleSessionApi.createChallenge()

    suspend fun signInWithGoogleIdToken(idToken: String, challengeId: String): AuthSession {
        val session = googleSessionApi.createSessionWithIdToken(idToken, challengeId, sessionStore.getSession())
        val authSession = AuthSession(session.sessionId, authApi.getMe(session))
        sessionStore.saveSessionId(session.sessionId)
        return authSession
    }

    suspend fun createAppleAuthChallenge(): AuthChallenge = appleSessionApi.createChallenge()

    suspend fun signInWithAppleCredential(credential: AppleSignInCredential, challengeId: String): AuthSession {
        val session = appleSessionApi.createSessionWithCredential(credential, challengeId, sessionStore.getSession())
        val authSession = AuthSession(session.sessionId, authApi.getMe(session))
        sessionStore.saveSessionId(session.sessionId)
        return authSession
    }

    suspend fun getMe(): User {
        return withAuthenticatedSession { session ->
            authApi.getMe(session)
        }
    }

    suspend fun signOut(): SignOutResult {
        val session = sessionStore.getSession()
        var remoteSucceeded = session == null
        try {
            session?.let {
                remoteSucceeded = runCatching {
                    authApi.logout(it)
                }.isSuccess
            }
        } finally {
            sessionStore.clearSessionId()
        }
        return if (remoteSucceeded) SignOutResult.Complete else SignOutResult.LocalOnly
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

enum class SignOutResult {
    Complete,
    LocalOnly,
}

private suspend fun SessionStore.getSession(): TwinteSession? =
    getSessionId()?.let(::TwinteSession)

private fun Throwable.isUnauthorized(): Boolean =
    this is TwinteApiException && status == HttpStatusCode.Unauthorized

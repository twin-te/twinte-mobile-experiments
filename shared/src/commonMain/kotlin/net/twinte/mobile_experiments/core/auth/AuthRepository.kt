package net.twinte.mobile_experiments.core.auth

import kotlinx.coroutines.CancellationException
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.api.TwinteNetworkException
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

class AuthRepository(
    private val sessionStore: SessionStore,
    private val authApi: AuthApi,
    private val googleSessionApi: GoogleSessionApi,
    private val appleSessionApi: AppleSessionApi,
    private val accountLocalDataSource: AccountLocalDataSource,
) : AuthService {
    override suspend fun restoreSession(): AuthSession? {
        val session = sessionStore.getSession() ?: run {
            accountLocalDataSource.clearActiveAccountAndData()
            return null
        }
        val cachedUser = accountLocalDataSource.getActiveUser()
        return runCatching {
            val user = authApi.getMe(session)
            accountLocalDataSource.saveActiveUser(user)
            AuthSession(session.sessionId, user)
        }.onFailure { error ->
            if (error.isUnauthorized()) {
                sessionStore.clearSessionId()
                accountLocalDataSource.clearActiveAccountAndData()
            }
        }.getOrElse { error ->
            if (error.isUnauthorized()) return null
            if (cachedUser != null && error.allowsOfflineSession()) {
                return AuthSession(
                    sessionId = session.sessionId,
                    user = cachedUser,
                    isOffline = true,
                )
            }
            throw error
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthSession {
        val session = googleSessionApi.createSessionWithIdToken(idToken, sessionStore.getSession())
        return validateAndSaveSession(session)
    }

    override suspend fun signInWithAppleCredential(credential: AppleSignInCredential): AuthSession {
        val session = appleSessionApi.createSessionWithCredential(credential, sessionStore.getSession())
        return validateAndSaveSession(session)
    }

    override suspend fun getMe(): User {
        return withAuthenticatedSession { session ->
            authApi.getMe(session).also {
                accountLocalDataSource.saveActiveUser(it)
            }
        }
    }

    override suspend fun signOut(): SignOutResult {
        val session = sessionStore.getSession()
        var remoteFailure: Throwable? = null
        try {
            if (session != null) {
                authApi.logout(session)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            remoteFailure = error
        } finally {
            sessionStore.clearSessionId()
            accountLocalDataSource.clearActiveAccountAndData()
        }
        return remoteFailure?.let(SignOutResult::LocalOnly)
            ?: SignOutResult.RemoteAndLocal
    }

    private suspend fun validateAndSaveSession(session: TwinteSession): AuthSession {
        val authSession = AuthSession(session.sessionId, authApi.getMe(session))
        accountLocalDataSource.saveActiveUser(authSession.user)
        sessionStore.saveSessionId(session.sessionId)
        return authSession
    }

    override suspend fun deleteUserAuthentication(provider: AuthProvider): AuthSession {
        return withAuthenticatedSession { session ->
            authApi.deleteUserAuthentication(session, provider)
            val user = authApi.getMe(session)
            accountLocalDataSource.saveActiveUser(user)
            AuthSession(session.sessionId, user)
        }
    }

    override suspend fun deleteAccount() {
        withAuthenticatedSession { session ->
            authApi.deleteAccount(session)
        }
        sessionStore.clearSessionId()
        accountLocalDataSource.clearActiveAccountAndData()
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
                accountLocalDataSource.clearActiveAccountAndData()
            }
        }.getOrThrow()
    }
}

data class AuthSession(
    val sessionId: String,
    val user: User,
    val isOffline: Boolean = false,
)

sealed interface SignOutResult {
    data object RemoteAndLocal : SignOutResult

    data class LocalOnly(val cause: Throwable) : SignOutResult
}

private fun Throwable.isUnauthorized(): Boolean =
    this is TwinteApiException && isUnauthorized

private fun Throwable.allowsOfflineSession(): Boolean =
    this is TwinteNetworkException ||
        (this is TwinteApiException && statusCode >= ServerErrorStatusCode)

private const val ServerErrorStatusCode = 500

package net.twinte.mobile_experiments.core.auth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.twinte.mobile_experiments.core.api.TwinteApiException

class AuthenticatedSessionProvider(
    private val sessionStore: SessionStore,
    private val accountLocalDataSource: AccountLocalDataSource,
) {
    suspend fun <T> withActiveSession(
        accountId: String,
        operation: suspend (TwinteSession) -> T,
    ): T {
        require(accountLocalDataSource.getActiveUser()?.id == accountId) {
            "The requested account is not active"
        }
        val session = requireNotNull(sessionStore.getSession()) { "No session" }
        return try {
            operation(session)
        } catch (error: Throwable) {
            if (error is TwinteApiException && error.isUnauthorized) {
                currentCoroutineContext().ensureActive()
                if (accountLocalDataSource.getActiveUser()?.id == accountId &&
                    sessionStore.getSessionId() == session.sessionId
                ) {
                    sessionStore.clearSessionId()
                    accountLocalDataSource.clearActiveAccountAndData()
                }
            }
            throw error
        }
    }
}

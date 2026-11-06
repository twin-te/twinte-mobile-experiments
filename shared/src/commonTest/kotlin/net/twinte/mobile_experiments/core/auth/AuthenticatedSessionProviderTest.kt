package net.twinte.mobile_experiments.core.auth

import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthenticatedSessionProviderTest {
    @Test
    fun suppliesSessionForActiveAccount() = runTest {
        val provider = provider()

        val sessionId = provider.withActiveSession("user-id") { it.sessionId }

        assertEquals("session-id", sessionId)
    }

    @Test
    fun clearsSessionAndAccountDataOnUnauthorized() = runTest {
        val sessionStore = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(user())
        val provider = AuthenticatedSessionProvider(sessionStore, accountStore)

        assertFailsWith<TwinteApiException> {
            provider.withActiveSession("user-id") {
                throw TwinteApiException(401, "unauthorized")
            }
        }

        assertNull(sessionStore.getSessionId())
        assertNull(accountStore.getActiveUser())
    }

    @Test
    fun keepsSessionAndAccountDataOnOtherFailures() = runTest {
        val sessionStore = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(user())
        val provider = AuthenticatedSessionProvider(sessionStore, accountStore)

        assertFailsWith<IllegalStateException> {
            provider.withActiveSession("user-id") {
                error("offline")
            }
        }

        assertEquals("session-id", sessionStore.getSessionId())
        assertNotNull(accountStore.getActiveUser())
    }

    @Test
    fun rejectsInactiveAccountBeforeRunningOperation() = runTest {
        val provider = provider()
        var operationStarted = false

        assertFailsWith<IllegalArgumentException> {
            provider.withActiveSession("other-user") {
                operationStarted = true
            }
        }

        assertFalse(operationStarted)
    }

    private fun provider(): AuthenticatedSessionProvider =
        AuthenticatedSessionProvider(
            sessionStore = MemorySessionStore("session-id"),
            accountLocalDataSource = MemoryAccountLocalDataSource(user()),
        )

    private fun user() = User("user-id", emptyList())
}

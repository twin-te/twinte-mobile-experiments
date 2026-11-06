package net.twinte.mobile_experiments.core.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun cancelledUnauthorizedRequestDoesNotClearSession() = runTest {
        val pendingResponse = CompletableDeferred<Unit>()
        val sessionStore = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(user())
        val provider = AuthenticatedSessionProvider(sessionStore, accountStore)
        val request = launch {
            provider.withActiveSession("user-id") {
                withContext(NonCancellable) { pendingResponse.await() }
                throw TwinteApiException(401, "unauthorized")
            }
        }
        runCurrent()

        request.cancel()
        pendingResponse.complete(Unit)
        request.join()

        assertEquals("session-id", sessionStore.getSessionId())
        assertEquals(user(), accountStore.getActiveUser())
    }

    @Test
    fun oldUnauthorizedRequestDoesNotClearReplacementSession() = runTest {
        val sessionStore = MemorySessionStore("old-session")
        val accountStore = MemoryAccountLocalDataSource(user())
        val provider = AuthenticatedSessionProvider(sessionStore, accountStore)

        assertFailsWith<TwinteApiException> {
            provider.withActiveSession("user-id") {
                sessionStore.saveSessionId("new-session")
                throw TwinteApiException(401, "unauthorized")
            }
        }

        assertEquals("new-session", sessionStore.getSessionId())
        assertEquals(user(), accountStore.getActiveUser())
    }

    @Test
    fun oldUnauthorizedRequestDoesNotClearReplacementAccount() = runTest {
        val sessionStore = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(user())
        val provider = AuthenticatedSessionProvider(sessionStore, accountStore)
        val replacementUser = User("other-user", emptyList())

        assertFailsWith<TwinteApiException> {
            provider.withActiveSession("user-id") {
                accountStore.saveActiveUser(replacementUser)
                throw TwinteApiException(401, "unauthorized")
            }
        }

        assertEquals("session-id", sessionStore.getSessionId())
        assertEquals(replacementUser, accountStore.getActiveUser())
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

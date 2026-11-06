package net.twinte.mobile_experiments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.SignOutResult
import net.twinte.mobile_experiments.core.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStateHolderTest {
    @Test
    fun initialStateWaitsForSessionRestoration() = runTest {
        val holder = AuthStateHolder(
            authService = FakeAuthService(),
            googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = backgroundScope,
        )
        assertEquals(AuthState.Unknown, holder.uiState.value.authState)
        assertEquals(true, holder.uiState.value.isLoading)
    }

    @Test
    fun cachedSessionIsVisibleBeforeOnlineValidationCompletes() = runTest {
        val cached = AuthSession("session-id", User("cached-user", emptyList()))
        val validated = AuthSession("session-id", User("updated-user", emptyList()))
        val pendingValidation = CompletableDeferred<AuthSession?>()
        val stateHolder = AuthStateHolder(
            authService = FakeAuthService(
                cachedResult = cached,
                restoreOperation = { pendingValidation.await() },
            ),
            googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = backgroundScope,
        )
        val restoration = launch { stateHolder.restoreSession() }
        runCurrent()

        assertEquals(cached, assertIs<AuthState.LoggedIn>(stateHolder.uiState.value.authState).session)
        assertEquals(false, stateHolder.uiState.value.isLoading)
        assertEquals("Showing saved data; checking session...", stateHolder.uiState.value.message)

        pendingValidation.complete(validated)
        restoration.join()
        assertEquals(validated, assertIs<AuthState.LoggedIn>(stateHolder.uiState.value.authState).session)
    }

    @Test
    fun loggingOutDuringValidationIgnoresLateResponse() = runTest {
        val cached = AuthSession("session-id", User("cached-user", emptyList()))
        val pendingValidation = CompletableDeferred<Unit>()
        val stateHolder = AuthStateHolder(
            authService = FakeAuthService(
                cachedResult = cached,
                restoreOperation = {
                    withContext(NonCancellable) { pendingValidation.await() }
                    cached
                },
            ),
            googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = backgroundScope,
        )
        val restoration = launch { stateHolder.restoreSession() }
        runCurrent()

        stateHolder.signOut()
        runCurrent()
        pendingValidation.complete(Unit)
        restoration.join()

        assertEquals(AuthState.LoggedOut, stateHolder.uiState.value.authState)
    }

    @Test
    fun offlineFallbackKeepsCachedSessionVisible() = runTest {
        val cached = AuthSession("session-id", User("cached-user", emptyList()))
        val stateHolder = AuthStateHolder(
            authService = FakeAuthService(
                cachedResult = cached,
                restoreOperation = { cached.copy(isOffline = true) },
            ),
            googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = backgroundScope,
        )

        stateHolder.restoreSession()

        assertEquals(cached.copy(isOffline = true), assertIs<AuthState.LoggedIn>(stateHolder.uiState.value.authState).session)
        assertEquals("Offline; using saved data", stateHolder.uiState.value.message)
    }

    @Test
    fun unauthorizedValidationRemovesCachedSession() = runTest {
        val cached = AuthSession("session-id", User("cached-user", emptyList()))
        val stateHolder = AuthStateHolder(
            authService = FakeAuthService(
                cachedResult = cached,
                restoreOperation = { null },
            ),
            googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = backgroundScope,
        )

        stateHolder.restoreSession()

        assertEquals(AuthState.LoggedOut, stateHolder.uiState.value.authState)
    }

    @Test
    fun canceledProviderRequestKeepsLoggedInState() = runTest {
        val existingSession = AuthSession("session-id", User("user-id", emptyList()))
        val authService = FakeAuthService(restoreResult = existingSession)
        val stateHolder = AuthStateHolder(
            authService = authService,
            googleIdTokenProvider = object : GoogleIdTokenProvider {
                override val isConfigured = true

                override suspend fun requestIdToken(): GoogleIdTokenResult =
                    GoogleIdTokenResult(isCanceled = true)
            },
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = this,
        )
        stateHolder.restoreSession()

        stateHolder.signInWithGoogle()
        advanceUntilIdle()

        val loggedIn = assertIs<AuthState.LoggedIn>(stateHolder.uiState.value.authState)
        assertEquals(existingSession, loggedIn.session)
        assertEquals("Canceled", stateHolder.uiState.value.message)
    }

    @Test
    fun localOnlyLogoutSignsOutAndReportsRemoteFailure() = runTest {
        val existingSession = AuthSession("session-id", User("user-id", emptyList()))
        val authService = FakeAuthService(
            restoreResult = existingSession,
            signOutResult = SignOutResult.LocalOnly(IllegalStateException("offline")),
        )
        val stateHolder = AuthStateHolder(
            authService = authService,
            googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
            appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
            scope = this,
        )
        stateHolder.restoreSession()

        stateHolder.signOut()
        advanceUntilIdle()

        assertEquals(AuthState.LoggedOut, stateHolder.uiState.value.authState)
        assertEquals("Signed out locally; remote logout failed", stateHolder.uiState.value.message)
    }

}

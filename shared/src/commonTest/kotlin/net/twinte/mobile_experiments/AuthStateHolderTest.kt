package net.twinte.mobile_experiments

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.auth.AppleSignInCredential
import net.twinte.mobile_experiments.core.auth.AuthService
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.SignOutResult
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStateHolderTest {
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

    private class FakeAuthService(
        private val restoreResult: AuthSession? = null,
        private val signOutResult: SignOutResult = SignOutResult.RemoteAndLocal,
    ) : AuthService {
        override suspend fun restoreSession(): AuthSession? = restoreResult

        override suspend fun signInWithGoogleIdToken(idToken: String): AuthSession =
            error("Not used")

        override suspend fun signInWithAppleCredential(credential: AppleSignInCredential): AuthSession =
            error("Not used")

        override suspend fun getMe(): User = error("Not used")

        override suspend fun signOut(): SignOutResult = signOutResult

        override suspend fun deleteUserAuthentication(provider: AuthProvider): AuthSession =
            error("Not used")

        override suspend fun deleteAccount() = error("Not used")
    }
}

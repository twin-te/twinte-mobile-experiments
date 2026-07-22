package net.twinte.mobile_experiments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.api.TwinteNetworkException
import net.twinte.mobile_experiments.core.auth.AuthCanceledException
import net.twinte.mobile_experiments.core.auth.AuthFailure
import net.twinte.mobile_experiments.core.auth.AuthService
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.SignOutResult
import net.twinte.mobile_experiments.core.domain.AuthProvider

internal data class AuthUiState(
    val authState: AuthState,
    val message: String,
    val isLoading: Boolean = false,
)

internal class AuthStateHolder(
    private val authService: AuthService,
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val appleSignInCredentialProvider: AppleSignInCredentialProvider,
    private val scope: CoroutineScope,
) {
    private val _uiState = MutableStateFlow(
        AuthUiState(
            authState = AuthState.Unknown,
            message = initialMessage(),
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    suspend fun restoreSession() {
        _uiState.update { it.copy(isLoading = true, message = "Restoring session...") }
        try {
            authService.restoreSession()?.let(::applySession)
                ?: _uiState.update { it.signedOut(initialMessage()) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            applyFailure(error)
        }
    }

    fun signInWithApple() = launchOperation("Opening Apple...") {
        val result = appleSignInCredentialProvider.requestCredential()
        if (result.isCanceled) throw AuthCanceledException()
        val credential = result.credential
            ?: throw CredentialUnavailableException()
        _uiState.update { it.copy(message = "Creating session...") }
        applySession(authService.signInWithAppleCredential(credential))
    }

    fun refreshMe() = launchOperation("Checking session...") {
        val currentSession = (_uiState.value.authState as? AuthState.LoggedIn)?.session
            ?: throw IllegalStateException("No authenticated UI session")
        val currentUser = authService.getMe()
        applySession(currentSession.copy(user = currentUser))
    }

    fun deleteUserAuthentication(provider: AuthProvider) =
        launchOperation("Unlinking ${provider.name}...") {
            applySession(
                session = authService.deleteUserAuthentication(provider),
                message = "Unlinked ${provider.name}",
            )
        }

    fun signOut() = launchOperation("Signing out...") {
        val message = when (authService.signOut()) {
            SignOutResult.RemoteAndLocal -> "Signed out"
            is SignOutResult.LocalOnly -> "Signed out locally; remote logout failed"
        }
        _uiState.update { it.signedOut(message) }
    }

    fun deleteAccount() = launchOperation("Deleting account...") {
        authService.deleteAccount()
        _uiState.update { it.signedOut("Deleted account") }
    }

    fun signInWithGoogle() = launchOperation("Opening Google...") {
        val result = googleIdTokenProvider.requestIdToken()
        if (result.isCanceled) throw AuthCanceledException()
        val idToken = result.idToken
            ?: throw CredentialUnavailableException()
        _uiState.update { it.copy(message = "Creating session...") }
        applySession(authService.signInWithGoogleIdToken(idToken))
    }

    private fun launchOperation(message: String, operation: suspend () -> Unit) {
        if (_uiState.value.isLoading) return
        scope.launch {
            _uiState.update { it.copy(isLoading = true, message = message) }
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    private fun applySession(session: AuthSession, message: String = "Signed in") {
        _uiState.update {
            it.copy(
                authState = AuthState.LoggedIn(session),
                message = message,
                isLoading = false,
            )
        }
    }

    private fun applyFailure(error: Throwable) {
        val failure = error.toAuthFailure()
        _uiState.update { state ->
            if (failure == AuthFailure.Unauthenticated) {
                state.signedOut(failure.toStatusMessage())
            } else {
                state.copy(
                    message = failure.toStatusMessage(),
                    isLoading = false,
                )
            }
        }
    }

    private fun initialMessage(): String =
        if (googleIdTokenProvider.isConfigured || appleSignInCredentialProvider.isConfigured) {
            "Not signed in"
        } else {
            "Login provider is not configured"
        }
}

private class CredentialUnavailableException : Exception()

private fun AuthUiState.signedOut(message: String): AuthUiState =
    copy(
        authState = AuthState.LoggedOut,
        message = message,
        isLoading = false,
    )

private fun Throwable.toAuthFailure(): AuthFailure =
    when (this) {
        is AuthCanceledException -> AuthFailure.Canceled
        is CredentialUnavailableException -> AuthFailure.CredentialUnavailable
        is TwinteNetworkException -> AuthFailure.Network(this)
        is TwinteApiException -> if (isUnauthorized) {
            AuthFailure.Unauthenticated
        } else {
            AuthFailure.Unexpected(this)
        }
        else -> AuthFailure.Unexpected(this)
    }

private fun AuthFailure.toStatusMessage(): String =
    when (this) {
        AuthFailure.Unauthenticated -> "Session expired"
        AuthFailure.Canceled -> "Canceled"
        AuthFailure.CredentialUnavailable -> "Login credential is unavailable"
        is AuthFailure.Network -> "Network error"
        is AuthFailure.Unexpected -> cause?.message ?: "Unknown error"
    }

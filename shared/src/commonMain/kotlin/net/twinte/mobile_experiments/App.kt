package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.http.HttpStatusCode
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.launch
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.api.ktor.KtorAppleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.KtorGoogleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteLoginHttpClient
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteHttpClient
import net.twinte.mobile_experiments.core.auth.AuthFailure
import net.twinte.mobile_experiments.core.auth.AuthCanceledException
import net.twinte.mobile_experiments.core.auth.AuthRepository
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.AppleSignInCredential
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.auth.SignOutResult
import net.twinte.mobile_experiments.core.auth.rememberSessionStore
import net.twinte.mobile_experiments.core.domain.AuthProvider

interface GoogleIdTokenProvider {
    val isConfigured: Boolean

    suspend fun requestIdToken(nonce: String): String?
}

object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
    override val isConfigured: Boolean = false

    override suspend fun requestIdToken(nonce: String): String? = null
}

interface AppleSignInCredentialProvider {
    val isConfigured: Boolean

    suspend fun requestCredential(nonce: String): AppleSignInCredential?
}

object UnavailableAppleSignInCredentialProvider : AppleSignInCredentialProvider {
    override val isConfigured: Boolean = false

    override suspend fun requestCredential(nonce: String): AppleSignInCredential? = null
}

@Composable
@Preview
fun App(
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
    sessionStore: SessionStore = rememberSessionStore(),
    appBaseUrl: String = "https://app.twinte.net",
) {
    val normalizedAppBaseUrl = appBaseUrl.trimEnd('/').ifBlank { "https://app.twinte.net" }
    val httpClient = rememberTwinteHttpClient()
    val loginHttpClient = rememberTwinteLoginHttpClient()
    val authRepository = remember(sessionStore, httpClient, loginHttpClient, normalizedAppBaseUrl) {
        AuthRepository(
            sessionStore = sessionStore,
            authApi = KtorAuthApi(apiBaseUrl = "$normalizedAppBaseUrl/api/v4", httpClient = httpClient),
            googleSessionApi = KtorGoogleSessionApi(appBaseUrl = normalizedAppBaseUrl, httpClient = loginHttpClient),
            appleSessionApi = KtorAppleSessionApi(appBaseUrl = normalizedAppBaseUrl, httpClient = loginHttpClient),
        )
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AuthScreen(
                googleIdTokenProvider = googleIdTokenProvider,
                appleSignInCredentialProvider = appleSignInCredentialProvider,
                authRepository = authRepository,
            )
        }
    }
}

@Composable
private fun AuthScreen(
    googleIdTokenProvider: GoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider,
    authRepository: AuthRepository,
) {
    val scope = rememberCoroutineScope()
    var uiState by remember(
        googleIdTokenProvider.isConfigured,
        appleSignInCredentialProvider.isConfigured,
    ) {
        mutableStateOf(
            AuthUiState(
                authState = AuthState.Unknown,
                message = initialMessage(googleIdTokenProvider, appleSignInCredentialProvider),
            ),
        )
    }
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }

    fun applySession(session: AuthSession, message: String = "Signed in") {
        uiState = uiState.copy(
            authState = AuthState.LoggedIn(session.user),
            sessionId = session.sessionId,
            message = message,
            isLoading = false,
        )
    }

    fun applyFailure(error: Throwable) {
        val failure = error.toAuthFailure()
        uiState = if (failure == AuthFailure.Unauthenticated) {
            uiState.signedOut(failure.toStatusMessage())
        } else {
            uiState.copy(
                message = failure.toStatusMessage(),
                isLoading = false,
            )
        }
    }

    LaunchedEffect(authRepository) {
        uiState = uiState.copy(isLoading = true, message = "Restoring session...")
        try {
            authRepository.restoreSession()?.let(::applySession)
                ?: run {
                    uiState = uiState.copy(
                        authState = AuthState.LoggedOut,
                        sessionId = null,
                        message = initialMessage(googleIdTokenProvider, appleSignInCredentialProvider),
                        isLoading = false,
                    )
                }
        } catch (error: Throwable) {
            applyFailure(error)
        }
    }

    fun signInWithApple() {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Opening Apple...")
            try {
                val challenge = authRepository.createAppleAuthChallenge()
                val credential = appleSignInCredentialProvider.requestCredential(challenge.nonce)
                    ?: throw AuthCanceledException()
                uiState = uiState.copy(message = "Creating session...")
                applySession(authRepository.signInWithAppleCredential(credential, challenge.id))
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    fun refreshMe() {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Checking session...")
            try {
                val currentUser = authRepository.getMe()
                uiState = uiState.copy(
                    authState = AuthState.LoggedIn(currentUser),
                    message = "Signed in",
                    isLoading = false,
                )
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    fun deleteUserAuthentication(provider: AuthProvider) {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Unlinking ${provider.name}...")
            try {
                applySession(
                    session = authRepository.deleteUserAuthentication(provider),
                    message = "Unlinked ${provider.name}",
                )
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    fun signOut() {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Signing out...")
            try {
                val result = authRepository.signOut()
                uiState = uiState.signedOut(
                    if (result == SignOutResult.Complete) {
                        "Signed out"
                    } else {
                        "Signed out locally; server sign-out failed"
                    },
                )
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    fun deleteAccount() {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Deleting account...")
            try {
                authRepository.deleteAccount()
                uiState = uiState.signedOut("Deleted account")
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Opening Google...")
            try {
                val challenge = authRepository.createGoogleAuthChallenge()
                val idToken = googleIdTokenProvider.requestIdToken(challenge.nonce)
                    ?: throw AuthCanceledException()
                uiState = uiState.copy(message = "Creating session...")
                applySession(authRepository.signInWithGoogleIdToken(idToken, challenge.id))
            } catch (error: Throwable) {
                applyFailure(error)
            }
        }
    }

    val loggedInUser = (uiState.authState as? AuthState.LoggedIn)?.user
    val linkedProviders = loggedInUser?.authentications?.map { it.provider }?.toSet().orEmpty()
    val canUnlinkProvider = linkedProviders.size > 1

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Twin:te Auth", style = MaterialTheme.typography.headlineMedium)
        Text(uiState.message, style = MaterialTheme.typography.bodyLarge)
        uiState.sessionId?.let {
            Text("Session: ${it.take(8)}...", style = MaterialTheme.typography.bodyMedium)
        }
        loggedInUser?.let {
            Text("User ID: ${it.id}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Providers: ${it.authentications.joinToString { auth -> auth.provider.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = googleIdTokenProvider.isConfigured && !uiState.isLoading,
                onClick = ::signInWithGoogle,
            ) {
                Text("Google")
            }
            Button(
                enabled = appleSignInCredentialProvider.isConfigured && !uiState.isLoading,
                onClick = ::signInWithApple,
            ) {
                Text("Apple")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = loggedInUser != null && !uiState.isLoading,
                onClick = ::refreshMe,
            ) {
                Text("getMe")
            }
            OutlinedButton(
                enabled = loggedInUser != null && !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = ::signOut,
            ) {
                Text("Logout")
            }
        }
        loggedInUser?.let {
            if (canUnlinkProvider) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (AuthProvider.Google in linkedProviders) {
                        OutlinedButton(
                            enabled = !uiState.isLoading,
                            onClick = { deleteUserAuthentication(AuthProvider.Google) },
                        ) {
                            Text("Unlink Google")
                        }
                    }
                    if (AuthProvider.Apple in linkedProviders) {
                        OutlinedButton(
                            enabled = !uiState.isLoading,
                            onClick = { deleteUserAuthentication(AuthProvider.Apple) },
                        ) {
                            Text("Unlink Apple")
                        }
                    }
                }
            }
            OutlinedButton(
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = { showDeleteAccountConfirmation = true },
            ) {
                Text("Delete Account")
            }
        }
    }

    if (showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirmation = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and cannot be undone.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        showDeleteAccountConfirmation = false
                        deleteAccount()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAccountConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private data class AuthUiState(
    val authState: AuthState,
    val sessionId: String? = null,
    val message: String,
    val isLoading: Boolean = false,
)

private fun AuthUiState.signedOut(message: String): AuthUiState =
    copy(
        authState = AuthState.LoggedOut,
        sessionId = null,
        message = message,
        isLoading = false,
    )

private fun initialMessage(
    googleIdTokenProvider: GoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider,
): String =
    if (googleIdTokenProvider.isConfigured || appleSignInCredentialProvider.isConfigured) {
        "Not signed in"
    } else {
        "Login provider is not configured"
    }

private fun Throwable.toAuthFailure(): AuthFailure =
    when (this) {
        is AuthCanceledException -> AuthFailure.Canceled
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> AuthFailure.Network(this)
        is TwinteApiException -> when (status) {
            HttpStatusCode.Unauthorized -> AuthFailure.Unauthenticated
            else -> AuthFailure.Unexpected(this)
        }
        else -> AuthFailure.Unexpected(this)
    }

private fun AuthFailure.toStatusMessage(): String =
    when (this) {
        AuthFailure.Unauthenticated -> "Session expired"
        AuthFailure.Canceled -> "Canceled"
        is AuthFailure.Network -> "Network error"
        is AuthFailure.Unexpected -> cause?.message ?: "Unknown error"
    }

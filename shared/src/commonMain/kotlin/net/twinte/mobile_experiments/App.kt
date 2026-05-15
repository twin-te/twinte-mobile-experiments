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
import kotlinx.coroutines.launch
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.KtorGoogleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteLoginHttpClient
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteHttpClient
import net.twinte.mobile_experiments.core.auth.AuthFailure
import net.twinte.mobile_experiments.core.auth.AuthRepository
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.auth.rememberSessionStore

interface GoogleIdTokenProvider {
    val isConfigured: Boolean

    suspend fun requestIdToken(): String?
}

object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
    override val isConfigured: Boolean = false

    override suspend fun requestIdToken(): String? = null
}

@Composable
@Preview
fun App(
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
    sessionStore: SessionStore = rememberSessionStore(),
) {
    val httpClient = rememberTwinteHttpClient()
    val loginHttpClient = rememberTwinteLoginHttpClient()
    val authRepository = remember(sessionStore, httpClient, loginHttpClient) {
        AuthRepository(
            sessionStore = sessionStore,
            authApi = KtorAuthApi(httpClient = httpClient),
            googleSessionApi = KtorGoogleSessionApi(httpClient = loginHttpClient),
        )
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            GoogleLoginScreen(
                googleIdTokenProvider = googleIdTokenProvider,
                authRepository = authRepository,
            )
        }
    }
}

@Composable
private fun GoogleLoginScreen(
    googleIdTokenProvider: GoogleIdTokenProvider,
    authRepository: AuthRepository,
) {
    val scope = rememberCoroutineScope()
    var uiState by remember(googleIdTokenProvider.isConfigured) {
        mutableStateOf(
            AuthUiState(
                authState = AuthState.Unknown,
                message = googleIdTokenProvider.initialMessage(),
            ),
        )
    }

    fun applySession(session: AuthSession) {
        uiState = uiState.copy(
            authState = AuthState.LoggedIn(session.user),
            sessionId = session.sessionId,
            message = "Signed in",
            isLoading = false,
        )
    }

    LaunchedEffect(authRepository) {
        uiState = uiState.copy(isLoading = true, message = "Restoring session...")
        try {
            authRepository.restoreSession()?.let(::applySession)
                ?: run {
                    uiState = uiState.copy(
                        authState = AuthState.LoggedOut,
                        sessionId = null,
                        message = googleIdTokenProvider.initialMessage(),
                        isLoading = false,
                    )
                }
        } catch (error: Throwable) {
            uiState = uiState.signedOut(error.toAuthFailure().toStatusMessage())
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
                uiState = uiState.signedOut(error.toAuthFailure().toStatusMessage())
            }
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            uiState = uiState.copy(isLoading = true, message = "Opening Google...")
            try {
                val idToken = googleIdTokenProvider.requestIdToken()
                    ?: error("Google ID token is missing")
                uiState = uiState.copy(message = "Creating session...")
                applySession(authRepository.signInWithGoogleIdToken(idToken))
            } catch (error: Throwable) {
                uiState = uiState.signedOut(error.toAuthFailure().toStatusMessage())
            }
        }
    }

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
        (uiState.authState as? AuthState.LoggedIn)?.user?.let {
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
            OutlinedButton(
                enabled = !uiState.isLoading,
                onClick = ::refreshMe,
            ) {
                Text("getMe")
            }
            OutlinedButton(
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = {
                    scope.launch {
                        authRepository.clearSession()
                        uiState = uiState.signedOut("Signed out locally")
                    }
                },
            ) {
                Text("Clear")
            }
        }
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

private fun GoogleIdTokenProvider.initialMessage(): String =
    if (isConfigured) {
        "Not signed in"
    } else {
        "Google client ID is not configured"
    }

private fun Throwable.toAuthFailure(): AuthFailure =
    when (this) {
        is TwinteApiException -> when (status) {
            HttpStatusCode.Unauthorized -> AuthFailure.Unauthenticated
            else -> AuthFailure.Unexpected(this)
        }
        else -> AuthFailure.Unexpected(this)
    }

private fun AuthFailure.toStatusMessage(): String =
    when (this) {
        AuthFailure.Unauthenticated -> "Session is not authorized"
        AuthFailure.Canceled -> "Canceled"
        is AuthFailure.Network -> "Network error"
        is AuthFailure.Unexpected -> cause?.message ?: "Unknown error"
    }

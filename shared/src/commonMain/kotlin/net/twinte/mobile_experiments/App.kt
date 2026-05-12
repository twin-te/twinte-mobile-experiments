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
import net.twinte.mobile_experiments.core.api.ktor.KtorApiException
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteHttpClient
import net.twinte.mobile_experiments.core.auth.AuthRepository
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.rememberSessionStore
import net.twinte.mobile_experiments.core.domain.User

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
) {
    val sessionStore = rememberSessionStore()
    val httpClient = rememberTwinteHttpClient()
    val authRepository = remember(sessionStore, httpClient) {
        AuthRepository(
            sessionStore = sessionStore,
            httpClient = httpClient,
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
    var isLoading by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<User?>(null) }
    var message by remember {
        mutableStateOf(
            if (googleIdTokenProvider.isConfigured) {
                "Not signed in"
            } else {
                "Google client ID is not configured"
            },
        )
    }

    fun applySession(session: AuthSession) {
        sessionId = session.sessionId
        user = session.user
        message = "Signed in"
    }

    LaunchedEffect(authRepository) {
        isLoading = true
        message = "Restoring session..."
        try {
            authRepository.restoreSession()?.let(::applySession)
                ?: run {
                    message = if (googleIdTokenProvider.isConfigured) {
                        "Not signed in"
                    } else {
                        "Google client ID is not configured"
                    }
                }
        } catch (error: Throwable) {
            user = null
            sessionId = null
            message = error.toStatusMessage()
        } finally {
            isLoading = false
        }
    }

    fun refreshMe() {
        scope.launch {
            isLoading = true
            message = "Checking session..."
            try {
                val currentUser = authRepository.getMe()
                user = currentUser
                message = "Signed in"
            } catch (error: Throwable) {
                user = null
                message = error.toStatusMessage()
            } finally {
                isLoading = false
            }
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            isLoading = true
            message = "Opening Google..."
            try {
                val idToken = googleIdTokenProvider.requestIdToken()
                    ?: error("Google ID token is missing")
                message = "Creating session..."
                applySession(authRepository.signInWithGoogleIdToken(idToken))
            } catch (error: Throwable) {
                user = null
                message = error.toStatusMessage()
            } finally {
                isLoading = false
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
        Text(message, style = MaterialTheme.typography.bodyLarge)
        sessionId?.let {
            Text("Session: ${it.take(8)}...", style = MaterialTheme.typography.bodyMedium)
        }
        user?.let {
            Text("User ID: ${it.id}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Providers: ${it.authentications.joinToString { auth -> auth.provider.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = googleIdTokenProvider.isConfigured && !isLoading,
                onClick = ::signInWithGoogle,
            ) {
                Text("Google")
            }
            OutlinedButton(
                enabled = !isLoading,
                onClick = ::refreshMe,
            ) {
                Text("getMe")
            }
            OutlinedButton(
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = {
                    scope.launch {
                        authRepository.clearSession()
                        sessionId = null
                        user = null
                        message = "Signed out locally"
                    }
                },
            ) {
                Text("Clear")
            }
        }
    }
}

private fun Throwable.toStatusMessage(): String =
    when (this) {
        is KtorApiException -> when (status) {
            HttpStatusCode.Unauthorized -> "Session is not authorized"
            else -> "API error: $status"
        }
        else -> message ?: "Unknown error"
    }

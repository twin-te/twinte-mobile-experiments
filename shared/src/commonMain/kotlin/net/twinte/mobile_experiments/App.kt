package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.api.ktor.KtorAppleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.KtorGoogleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteHttpClient
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteLoginHttpClient
import net.twinte.mobile_experiments.core.auth.AuthRepository
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.domain.AuthProvider

@Composable
fun App(
    sessionStore: SessionStore,
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
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
    val scope = rememberCoroutineScope()
    val stateHolder = remember(
        authRepository,
        googleIdTokenProvider,
        appleSignInCredentialProvider,
        scope,
    ) {
        AuthStateHolder(
            authService = authRepository,
            googleIdTokenProvider = googleIdTokenProvider,
            appleSignInCredentialProvider = appleSignInCredentialProvider,
            scope = scope,
        )
    }
    val uiState by stateHolder.uiState.collectAsState()

    LaunchedEffect(stateHolder) {
        stateHolder.restoreSession()
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AuthScreen(
                uiState = uiState,
                isGoogleConfigured = googleIdTokenProvider.isConfigured,
                isAppleConfigured = appleSignInCredentialProvider.isConfigured,
                onGoogleSignIn = stateHolder::signInWithGoogle,
                onAppleSignIn = stateHolder::signInWithApple,
                onRefresh = stateHolder::refreshMe,
                onUnlinkProvider = stateHolder::deleteUserAuthentication,
                onSignOut = stateHolder::signOut,
                onDeleteAccount = stateHolder::deleteAccount,
            )
        }
    }
}

@Composable
@Preview
private fun AppPreview() {
    App(sessionStore = remember { MemorySessionStore() })
}

@Composable
private fun AuthScreen(
    uiState: AuthUiState,
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onUnlinkProvider: (AuthProvider) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }
    val loggedInState = uiState.authState as? AuthState.LoggedIn
    val loggedInUser = loggedInState?.user
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
        loggedInState?.session?.sessionId?.let {
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
                enabled = isGoogleConfigured && !uiState.isLoading,
                onClick = onGoogleSignIn,
            ) {
                Text("Google")
            }
            Button(
                enabled = isAppleConfigured && !uiState.isLoading,
                onClick = onAppleSignIn,
            ) {
                Text("Apple")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = loggedInUser != null && !uiState.isLoading,
                onClick = onRefresh,
            ) {
                Text("getMe")
            }
            OutlinedButton(
                enabled = loggedInUser != null && !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = onSignOut,
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
                            onClick = { onUnlinkProvider(AuthProvider.Google) },
                        ) {
                            Text("Unlink Google")
                        }
                    }
                    if (AuthProvider.Apple in linkedProviders) {
                        OutlinedButton(
                            enabled = !uiState.isLoading,
                            onClick = { onUnlinkProvider(AuthProvider.Apple) },
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
                        onDeleteAccount()
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

package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.domain.AuthProvider

@Composable
internal fun AuthenticationGate(
    uiState: AuthUiState,
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    signedInContent: @Composable (AuthState.LoggedIn) -> Unit,
) {
    val state = uiState.authState
    when {
        state is AuthState.LoggedIn -> key(state.user.id) { signedInContent(state) }
        state == AuthState.Unknown && uiState.isLoading -> Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(uiState.message)
            }
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Twin:te", style = MaterialTheme.typography.headlineMedium)
            Text(uiState.message)
            if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ProviderButtons(
                isGoogleConfigured = isGoogleConfigured,
                isAppleConfigured = isAppleConfigured,
                enabled = !uiState.isLoading,
                onGoogle = onGoogleSignIn,
                onApple = onAppleSignIn,
            )
        }
    }
}

@Composable
internal fun AccountScreen(
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
    val user = (uiState.authState as? AuthState.LoggedIn)?.user ?: return
    val providers = user.authentications.map { it.provider }.toSet()
    var confirmDeletion by rememberSaveable { mutableStateOf(false) }

    ScreenColumn {
        Text(uiState.message)
        Text("User ID: ${user.id}", style = MaterialTheme.typography.bodySmall)
        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        RefreshButton(isLoading = uiState.isLoading, onClick = onRefresh)
        HorizontalDivider()
        Text("Linked accounts", style = MaterialTheme.typography.titleMedium)
        providers.forEach { provider ->
            Text(provider.name)
            if (providers.size > 1) {
                OutlinedButton(
                    enabled = !uiState.isLoading,
                    onClick = { onUnlinkProvider(provider) },
                ) {
                    Text("Unlink ${provider.name}")
                }
            }
        }
        ProviderButtons(
            isGoogleConfigured = isGoogleConfigured && AuthProvider.Google !in providers,
            isAppleConfigured = isAppleConfigured && AuthProvider.Apple !in providers,
            enabled = !uiState.isLoading,
            onGoogle = onGoogleSignIn,
            onApple = onAppleSignIn,
            linking = true,
        )
        HorizontalDivider()
        OutlinedButton(enabled = !uiState.isLoading, onClick = onSignOut) {
            Text("Log out")
        }
        OutlinedButton(
            enabled = !uiState.isLoading,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = { confirmDeletion = true },
        ) {
            Text("Delete account")
        }
    }
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isLoading) confirmDeletion = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and cannot be undone.") },
            confirmButton = {
                Button(
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmDeletion = false
                        onDeleteAccount()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !uiState.isLoading,
                    onClick = { confirmDeletion = false },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProviderButtons(
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
    enabled: Boolean,
    onGoogle: () -> Unit,
    onApple: () -> Unit,
    linking: Boolean = false,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isGoogleConfigured) {
            Button(enabled = enabled, onClick = onGoogle) {
                Text(if (linking) "Link Google" else "Google")
            }
        }
        if (isAppleConfigured) {
            Button(enabled = enabled, onClick = onApple) {
                Text(if (linking) "Link Apple" else "Apple")
            }
        }
    }
}

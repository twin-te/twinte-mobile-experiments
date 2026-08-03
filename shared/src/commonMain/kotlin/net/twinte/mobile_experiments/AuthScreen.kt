package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.domain.AuthProvider

@Composable
internal fun AuthScreen(
    uiState: AuthUiState,
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onUnlinkProvider: (AuthProvider) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    timetableUiState: TimetableUiState,
    onLoadTimetable: () -> Unit,
    dailyTimetableUiState: DailyTimetableUiState,
    onLoadDailyTimetable: () -> Unit,
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
            .verticalScroll(rememberScrollState())
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
            TimetableSection(
                authIsLoading = uiState.isLoading,
                timetableUiState = timetableUiState,
                onLoadTimetable = onLoadTimetable,
                dailyTimetableUiState = dailyTimetableUiState,
                onLoadDailyTimetable = onLoadDailyTimetable,
            )
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
        DeleteAccountDialog(
            onConfirm = {
                showDeleteAccountConfirmation = false
                onDeleteAccount()
            },
            onDismiss = { showDeleteAccountConfirmation = false },
        )
    }
}

@Composable
private fun TimetableSection(
    authIsLoading: Boolean,
    timetableUiState: TimetableUiState,
    onLoadTimetable: () -> Unit,
    dailyTimetableUiState: DailyTimetableUiState,
    onLoadDailyTimetable: () -> Unit,
) {
    OutlinedButton(
        enabled = !authIsLoading && !timetableUiState.isLoading,
        onClick = onLoadTimetable,
    ) {
        Text("Load Timetable")
    }
    Text(timetableUiState.message, style = MaterialTheme.typography.bodyMedium)
    timetableUiState.courseCount?.let { Text("Registered courses: $it") }
    timetableUiState.tagCount?.let { Text("Tags: $it") }
    timetableUiState.firstCourseName?.let { Text("First course: $it") }
    timetableUiState.lastSuccessfulSyncAt?.let { Text("Last sync: $it") }
    if (timetableUiState.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    OutlinedButton(
        enabled = !authIsLoading && !dailyTimetableUiState.isLoading,
        onClick = onLoadDailyTimetable,
    ) {
        Text("Load Today's Schedule")
    }
    Text(dailyTimetableUiState.message, style = MaterialTheme.typography.bodyMedium)
    Text("Date: ${dailyTimetableUiState.date}", style = MaterialTheme.typography.bodyMedium)
    dailyTimetableUiState.moduleLabel?.let { Text("Module: $it") }
    dailyTimetableUiState.eventLabel?.let { Text("Day: $it") }
    dailyTimetableUiState.lectureCount?.let { Text("Today's courses: $it") }
    dailyTimetableUiState.firstCourseName?.let { Text("First today: $it") }
    dailyTimetableUiState.lastSuccessfulSyncAt?.let { Text("Daily sync: $it") }
    if (dailyTimetableUiState.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account?") },
        text = { Text("This permanently deletes your account and cannot be undone.") },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = onConfirm,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

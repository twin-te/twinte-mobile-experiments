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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import net.twinte.mobile_experiments.core.api.ktor.KtorAppleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.KtorGoogleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.KtorTimetableApi
import net.twinte.mobile_experiments.core.api.ktor.KtorUnifiedApi
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteHttpClient
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteLoginHttpClient
import net.twinte.mobile_experiments.core.auth.AuthRepository
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.database.RoomAccountLocalDataSource
import net.twinte.mobile_experiments.core.database.RoomDailyTimetableLocalDataSource
import net.twinte.mobile_experiments.core.database.RoomTimetableLocalDataSource
import net.twinte.mobile_experiments.core.database.rememberTwinteDatabase
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.timetable.DailyCourseSlot
import net.twinte.mobile_experiments.core.timetable.DailyTimetableRepository
import net.twinte.mobile_experiments.core.timetable.TimetableRepository

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
    val database = rememberTwinteDatabase()
    val accountLocalDataSource = remember(database) {
        RoomAccountLocalDataSource(database.accountDao())
    }
    val authRepository = remember(
        sessionStore,
        accountLocalDataSource,
        httpClient,
        loginHttpClient,
        normalizedAppBaseUrl,
    ) {
        AuthRepository(
            sessionStore = sessionStore,
            authApi = KtorAuthApi(apiBaseUrl = "$normalizedAppBaseUrl/api/v4", httpClient = httpClient),
            googleSessionApi = KtorGoogleSessionApi(appBaseUrl = normalizedAppBaseUrl, httpClient = loginHttpClient),
            appleSessionApi = KtorAppleSessionApi(appBaseUrl = normalizedAppBaseUrl, httpClient = loginHttpClient),
            accountLocalDataSource = accountLocalDataSource,
        )
    }
    val timetableRepository = remember(
        sessionStore,
        accountLocalDataSource,
        database,
        httpClient,
        normalizedAppBaseUrl,
    ) {
        TimetableRepository(
            sessionStore = sessionStore,
            accountLocalDataSource = accountLocalDataSource,
            timetableApi = KtorTimetableApi(apiBaseUrl = "$normalizedAppBaseUrl/api/v4", httpClient = httpClient),
            localDataSource = RoomTimetableLocalDataSource(database.timetableDao()),
        )
    }
    val dailyTimetableRepository = remember(
        sessionStore,
        accountLocalDataSource,
        database,
        httpClient,
        normalizedAppBaseUrl,
    ) {
        DailyTimetableRepository(
            sessionStore = sessionStore,
            accountLocalDataSource = accountLocalDataSource,
            api = KtorUnifiedApi(apiBaseUrl = "$normalizedAppBaseUrl/api/v4", httpClient = httpClient),
            localDataSource = RoomDailyTimetableLocalDataSource(database.dailyTimetableDao()),
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
    var timetableUiState by remember { mutableStateOf(TimetableUiState()) }
    var dailyTimetableUiState by remember { mutableStateOf(DailyTimetableUiState()) }
    val today = remember {
        Clock.System.todayIn(TimeZone.currentSystemDefault())
    }

    val loggedInUser = (uiState.authState as? AuthState.LoggedIn)?.user

    fun loadTimetable() {
        if (timetableUiState.isLoading) return
        val accountId = loggedInUser?.id ?: return
        scope.launch {
            timetableUiState = timetableUiState.copy(isLoading = true, message = "Refreshing timetable...")
            try {
                timetableRepository.refresh(accountId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                stateHolder.handleExternalFailure(error)
                timetableUiState = timetableUiState.copy(
                    message = "Refresh failed; showing saved timetable",
                    isLoading = false,
                )
            }
        }
    }

    fun loadDailyTimetable() {
        if (dailyTimetableUiState.isLoading) return
        val accountId = loggedInUser?.id ?: return
        scope.launch {
            dailyTimetableUiState = dailyTimetableUiState.copy(
                isLoading = true,
                message = "Refreshing today's schedule...",
            )
            try {
                dailyTimetableRepository.refresh(accountId, today)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                stateHolder.handleExternalFailure(error)
                dailyTimetableUiState = dailyTimetableUiState.copy(
                    message = "Refresh failed; showing saved daily schedule",
                    isLoading = false,
                )
            }
        }
    }

    LaunchedEffect(stateHolder) {
        stateHolder.restoreSession()
    }
    LaunchedEffect(loggedInUser?.id, timetableRepository) {
        val accountId = loggedInUser?.id
        if (accountId == null) {
            timetableUiState = TimetableUiState()
            return@LaunchedEffect
        }
        timetableRepository.observeTimetable(accountId).collect { snapshot ->
            timetableUiState = TimetableUiState(
                message = if (snapshot.lastSuccessfulSyncAtMillis == null) {
                    "No saved timetable"
                } else {
                    "Showing saved timetable"
                },
                courseCount = snapshot.courses.size,
                tagCount = snapshot.tags.size,
                firstCourseName = snapshot.courses.firstOrNull()?.name,
                lastSuccessfulSyncAtMillis = snapshot.lastSuccessfulSyncAtMillis,
            )
        }
    }
    LaunchedEffect(loggedInUser?.id, today, dailyTimetableRepository) {
        val accountId = loggedInUser?.id
        if (accountId == null) {
            dailyTimetableUiState = DailyTimetableUiState(date = today.toString())
            return@LaunchedEffect
        }
        dailyTimetableRepository.observe(accountId, today).collect { snapshot ->
            dailyTimetableUiState = if (snapshot == null) {
                DailyTimetableUiState(date = today.toString())
            } else {
                val timetable = snapshot.timetable
                DailyTimetableUiState(
                    message = "Showing saved daily schedule",
                    date = timetable.date,
                    moduleLabel = timetable.module.label,
                    eventLabel = timetable.eventLabel,
                    lectureCount = timetable.lectureCount,
                    firstCourseName = timetable.slots
                        .filterIsInstance<DailyCourseSlot.Course>()
                        .firstOrNull()
                        ?.name,
                    lastSuccessfulSyncAtMillis = snapshot.lastSuccessfulSyncAtMillis,
                )
            }
        }
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
                timetableUiState = timetableUiState,
                onLoadTimetable = ::loadTimetable,
                dailyTimetableUiState = dailyTimetableUiState,
                onLoadDailyTimetable = ::loadDailyTimetable,
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
            OutlinedButton(
                enabled = !uiState.isLoading && !timetableUiState.isLoading,
                onClick = onLoadTimetable,
            ) {
                Text("Load Timetable")
            }
            Text(timetableUiState.message, style = MaterialTheme.typography.bodyMedium)
            timetableUiState.courseCount?.let { count ->
                Text("Registered courses: $count", style = MaterialTheme.typography.bodyMedium)
            }
            timetableUiState.tagCount?.let { count ->
                Text("Tags: $count", style = MaterialTheme.typography.bodyMedium)
            }
            timetableUiState.firstCourseName?.let { name ->
                Text("First course: $name", style = MaterialTheme.typography.bodyMedium)
            }
            timetableUiState.lastSuccessfulSyncAtMillis?.let { syncedAt ->
                Text("Last sync: $syncedAt", style = MaterialTheme.typography.bodyMedium)
            }
            if (timetableUiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(
                enabled = !uiState.isLoading && !dailyTimetableUiState.isLoading,
                onClick = onLoadDailyTimetable,
            ) {
                Text("Load Today's Schedule")
            }
            Text(dailyTimetableUiState.message, style = MaterialTheme.typography.bodyMedium)
            Text("Date: ${dailyTimetableUiState.date}", style = MaterialTheme.typography.bodyMedium)
            dailyTimetableUiState.moduleLabel?.let {
                Text("Module: $it", style = MaterialTheme.typography.bodyMedium)
            }
            dailyTimetableUiState.eventLabel?.let {
                Text("Day: $it", style = MaterialTheme.typography.bodyMedium)
            }
            dailyTimetableUiState.lectureCount?.let {
                Text("Today's courses: $it", style = MaterialTheme.typography.bodyMedium)
            }
            dailyTimetableUiState.firstCourseName?.let {
                Text("First today: $it", style = MaterialTheme.typography.bodyMedium)
            }
            dailyTimetableUiState.lastSuccessfulSyncAtMillis?.let {
                Text("Daily sync: $it", style = MaterialTheme.typography.bodyMedium)
            }
            if (dailyTimetableUiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
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

private data class TimetableUiState(
    val message: String = "No saved timetable",
    val isLoading: Boolean = false,
    val courseCount: Int? = null,
    val tagCount: Int? = null,
    val firstCourseName: String? = null,
    val lastSuccessfulSyncAtMillis: Long? = null,
)

private data class DailyTimetableUiState(
    val message: String = "No saved daily schedule",
    val isLoading: Boolean = false,
    val date: String = "",
    val moduleLabel: String? = null,
    val eventLabel: String? = null,
    val lectureCount: Int? = null,
    val firstCourseName: String? = null,
    val lastSuccessfulSyncAtMillis: Long? = null,
)

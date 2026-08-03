package net.twinte.mobile_experiments

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.SessionStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Composable
fun App(
    sessionStore: SessionStore,
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
    appBaseUrl: String = DefaultAppBaseUrl,
) {
    val dependencies = rememberAppDependencies(sessionStore, appBaseUrl)
    val scope = rememberCoroutineScope()
    val authStateHolder = remember(
        dependencies.authService,
        googleIdTokenProvider,
        appleSignInCredentialProvider,
        scope,
    ) {
        AuthStateHolder(
            authService = dependencies.authService,
            googleIdTokenProvider = googleIdTokenProvider,
            appleSignInCredentialProvider = appleSignInCredentialProvider,
            scope = scope,
        )
    }
    var today by remember { mutableStateOf(currentLocalDate()) }
    val timetableStateHolder = remember(dependencies, authStateHolder, scope) {
        TimetableStateHolder(
            timetableService = dependencies.timetableService,
            dailyTimetableService = dependencies.dailyTimetableService,
            initialDate = today,
            scope = scope,
            onFailure = authStateHolder::handleExternalFailure,
        )
    }

    val authUiState by authStateHolder.uiState.collectAsState()
    val timetableUiState by timetableStateHolder.timetableUiState.collectAsState()
    val dailyTimetableUiState by timetableStateHolder.dailyTimetableUiState.collectAsState()
    val loggedInUser = (authUiState.authState as? AuthState.LoggedIn)?.user

    LaunchedEffect(authStateHolder) {
        authStateHolder.restoreSession()
    }
    LaunchedEffect(Unit) {
        while (true) {
            today = currentLocalDate()
            delay(1.minutes)
        }
    }
    LaunchedEffect(loggedInUser?.id, today, timetableStateHolder) {
        timetableStateHolder.bind(loggedInUser?.id, today)
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AuthScreen(
                uiState = authUiState,
                isGoogleConfigured = googleIdTokenProvider.isConfigured,
                isAppleConfigured = appleSignInCredentialProvider.isConfigured,
                onGoogleSignIn = authStateHolder::signInWithGoogle,
                onAppleSignIn = authStateHolder::signInWithApple,
                onRefresh = authStateHolder::refreshMe,
                onUnlinkProvider = authStateHolder::deleteUserAuthentication,
                onSignOut = authStateHolder::signOut,
                onDeleteAccount = authStateHolder::deleteAccount,
                timetableUiState = timetableUiState,
                onLoadTimetable = timetableStateHolder::refreshTimetable,
                onPreviousYear = timetableStateHolder::selectPreviousYear,
                onNextYear = timetableStateHolder::selectNextYear,
                onSelectModule = timetableStateHolder::selectModule,
                dailyTimetableUiState = dailyTimetableUiState,
                onLoadDailyTimetable = timetableStateHolder::refreshDailyTimetable,
            )
        }
    }
}

private fun currentLocalDate() = Clock.System.todayIn(TimeZone.currentSystemDefault())

@Composable
@Preview
private fun AppPreview() {
    App(sessionStore = remember { MemorySessionStore() })
}

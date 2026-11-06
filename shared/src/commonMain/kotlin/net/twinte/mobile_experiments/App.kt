package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
    val timetableStateHolder = remember(dependencies, authStateHolder, scope) {
        TimetableStateHolder(
            timetableService = dependencies.timetableService,
            dailyTimetableService = dependencies.dailyTimetableService,
            initialDate = currentLocalDate(),
            scope = scope,
            onFailure = authStateHolder::handleExternalFailure,
        )
    }

    val authUiState by authStateHolder.uiState.collectAsState()
    val timetableUiState by timetableStateHolder.timetableUiState.collectAsState()
    val courseOperationsUiState by timetableStateHolder.courseOperationsUiState.collectAsState()
    val dailyTimetableUiState by timetableStateHolder.dailyTimetableUiState.collectAsState()
    val loggedInState = authUiState.authState as? AuthState.LoggedIn
    val loggedInUser = loggedInState?.user
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(authStateHolder) {
        authStateHolder.restoreSession()
    }
    LaunchedEffect(loggedInUser?.id, loggedInState?.session?.sessionId, lifecycleOwner, timetableStateHolder) {
        val accountId = loggedInUser?.id
        timetableStateHolder.bind(accountId, currentLocalDate())
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (accountId == null) return@repeatOnLifecycle
            var date = currentLocalDate()
            timetableStateHolder.bind(accountId, date)
            timetableStateHolder.syncIfStale()
            while (true) {
                delay(1.minutes)
                val currentDate = currentLocalDate()
                if (currentDate != date) {
                    date = currentDate
                    timetableStateHolder.bind(accountId, date)
                    timetableStateHolder.syncIfStale()
                }
            }
        }
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
                courseOperationsUiState = courseOperationsUiState,
                onSearchCourses = timetableStateHolder::searchCourses,
                onRegisterCourse = timetableStateHolder::registerCourse,
                onUpdateCourse = timetableStateHolder::updateCourse,
                onDeleteCourse = timetableStateHolder::deleteCourse,
                onCreateTag = timetableStateHolder::createTag,
                onUpdateTag = timetableStateHolder::updateTag,
                onDeleteTag = timetableStateHolder::deleteTag,
                onMoveTag = timetableStateHolder::moveTag,
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

package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
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
    val authUiState by authStateHolder.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authStateHolder) {
        authStateHolder.restoreSession()
    }

    TwinteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AuthenticationGate(
                uiState = authUiState,
                isGoogleConfigured = googleIdTokenProvider.isConfigured,
                isAppleConfigured = appleSignInCredentialProvider.isConfigured,
                onGoogleSignIn = authStateHolder::signInWithGoogle,
                onAppleSignIn = authStateHolder::signInWithApple,
            ) { signedIn ->
                AuthenticatedApp(
                    session = signedIn.session,
                    dependencies = dependencies,
                    authUiState = authUiState,
                    authStateHolder = authStateHolder,
                    isGoogleConfigured = googleIdTokenProvider.isConfigured,
                    isAppleConfigured = appleSignInCredentialProvider.isConfigured,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(
    session: AuthSession,
    dependencies: AppDependencies,
    authUiState: AuthUiState,
    authStateHolder: AuthStateHolder,
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
) {
    // The gate keys this composition by account, so logout also cancels its pending work.
    val scope = rememberCoroutineScope()
    val timetableStateHolder = rememberTimetableStateHolder(
        timetableService = dependencies.timetableService,
        dailyTimetableService = dependencies.dailyTimetableService,
        initialDate = currentLocalDate(),
        scope = scope,
        onFailure = authStateHolder::handleExternalFailure,
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(session.user.id, session.sessionId, lifecycleOwner, timetableStateHolder) {
        val accountId = session.user.id
        timetableStateHolder.bind(accountId, currentLocalDate())
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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

    AppNavigation(
        authUiState = authUiState,
        authStateHolder = authStateHolder,
        timetableStateHolder = timetableStateHolder,
        isGoogleConfigured = isGoogleConfigured,
        isAppleConfigured = isAppleConfigured,
    )
}

private fun currentLocalDate() = Clock.System.todayIn(TimeZone.currentSystemDefault())

@Composable
@Preview
private fun AppPreview() {
    App(sessionStore = remember { MemorySessionStore() })
}

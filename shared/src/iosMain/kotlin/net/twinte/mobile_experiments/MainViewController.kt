package net.twinte.mobile_experiments

import androidx.compose.ui.window.ComposeUIViewController
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.auth.rememberSessionStore

fun MainViewController(
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
    sessionStore: SessionStore? = null,
) = ComposeUIViewController {
    App(
        googleIdTokenProvider = googleIdTokenProvider,
        sessionStore = sessionStore ?: rememberSessionStore(),
    )
}

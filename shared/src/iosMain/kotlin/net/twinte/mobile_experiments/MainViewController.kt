package net.twinte.mobile_experiments

import androidx.compose.ui.window.ComposeUIViewController
import net.twinte.mobile_experiments.core.auth.SessionStore

fun MainViewController(
    googleIdTokenProvider: GoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider,
    sessionStore: SessionStore,
    appBaseUrl: String = "https://app.twinte.net",
) = ComposeUIViewController {
    App(
        googleIdTokenProvider = googleIdTokenProvider,
        appleSignInCredentialProvider = appleSignInCredentialProvider,
        sessionStore = sessionStore,
        appBaseUrl = appBaseUrl,
    )
}

package net.twinte.mobile_experiments

import androidx.compose.ui.window.ComposeUIViewController
import net.twinte.mobile_experiments.core.auth.SessionStore

fun MainViewController(
    googleIdTokenProvider: GoogleIdTokenProvider,
    appleSignInCredentialProvider: AppleSignInCredentialProvider,
    sessionStore: SessionStore,
) = ComposeUIViewController {
    App(
        googleIdTokenProvider = googleIdTokenProvider,
        appleSignInCredentialProvider = appleSignInCredentialProvider,
        sessionStore = sessionStore,
    )
}

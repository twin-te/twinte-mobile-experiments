package net.twinte.mobile_experiments

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
) = ComposeUIViewController {
    App(googleIdTokenProvider = googleIdTokenProvider)
}

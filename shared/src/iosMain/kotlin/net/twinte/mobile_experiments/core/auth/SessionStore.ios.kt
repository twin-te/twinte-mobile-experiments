package net.twinte.mobile_experiments.core.auth

import androidx.compose.runtime.Composable

@Composable
actual fun rememberSessionStore(): SessionStore =
    error("iOS SessionStore must be provided by the app layer.")

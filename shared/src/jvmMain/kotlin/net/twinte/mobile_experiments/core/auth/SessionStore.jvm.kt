package net.twinte.mobile_experiments.core.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberSessionStore(): SessionStore = remember { MemorySessionStore() }

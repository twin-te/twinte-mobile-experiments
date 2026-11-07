package net.twinte.mobile_experiments.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.ktor.http.Url

// The JVM target runs UI tests, not a desktop application; native browser rendering is mobile-only.
internal actual class BrowserController internal constructor(url: Url) {
    actual val state = BrowserState(url, isLoading = false)
    actual val currentUrl: Url get() = state.url
    actual fun goBack(): Boolean = false
    actual fun reload() = Unit
}

@Composable
internal actual fun rememberBrowserController(url: Url): BrowserController = remember(url) { BrowserController(url) }

@Composable
internal actual fun BrowserView(controller: BrowserController, onOpenExternal: (String) -> Unit, modifier: Modifier) { Box(modifier) }

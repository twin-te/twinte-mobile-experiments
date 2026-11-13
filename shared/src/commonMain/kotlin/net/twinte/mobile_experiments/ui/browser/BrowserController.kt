package net.twinte.mobile_experiments.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.http.Url

internal data class BrowserState(
    val url: Url,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val isAtTop: Boolean = true,
)

internal expect class BrowserController {
    val state: BrowserState
    val currentUrl: Url
    // Query the native history at the time of the action, including same-document navigation.
    fun goBack(): Boolean
    fun reload()
}

@Composable
internal expect fun rememberBrowserController(url: Url): BrowserController

@Composable
internal expect fun BrowserView(controller: BrowserController, onOpenExternal: (String) -> Unit, modifier: Modifier = Modifier)

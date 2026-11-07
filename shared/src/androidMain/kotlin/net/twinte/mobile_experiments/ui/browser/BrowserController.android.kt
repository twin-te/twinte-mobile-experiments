package net.twinte.mobile_experiments.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.SslErrorHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.http.Url

internal actual class BrowserController internal constructor(internal val initialUrl: Url) {
    private var currentState by mutableStateOf(BrowserState(initialUrl))
    actual val state: BrowserState get() = currentState
    internal var view: WebView? = null

    actual val currentUrl: Url get() = view?.url?.let(::browserUrlOrNull) ?: state.url

    actual fun goBack(): Boolean {
        val view = view?.takeIf { it.canGoBack() } ?: return false
        view.goBack()
        return true
    }
    actual fun reload() { view?.reload() }

    internal fun update(view: WebView, loading: Boolean = state.isLoading, failed: Boolean = state.failed) {
        currentState = BrowserState(
            url = view.url?.let(::browserUrlOrNull) ?: state.url,
            isLoading = loading, failed = failed, isAtTop = view.scrollY <= 0,
        )
    }
}

@Composable
internal actual fun rememberBrowserController(url: Url): BrowserController = remember(url) { BrowserController(url) }

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun BrowserView(controller: BrowserController, onOpenExternal: (String) -> Unit, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                controller.view = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame && request.url.toString() == "about:blank") return false
                        val url = browserUrlOrNull(request.url.toString())
                        if (url != null) {
                            if (request.isForMainFrame && request.hasGesture() && isMapUrl(url)) {
                                onOpenExternal(url.toString())
                                return true
                            }
                            return false
                        }
                        if (request.isForMainFrame && request.hasGesture() && request.url.scheme in listOf("mailto", "tel")) {
                            onOpenExternal(request.url.toString())
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        controller.update(view, loading = true, failed = false)
                    }

                    override fun onPageFinished(view: WebView, url: String?) { controller.update(view, loading = false) }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        controller.update(view)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) controller.update(view, loading = false, failed = true)
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame) controller.update(view, loading = false, failed = true)
                    }

                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel()
                        controller.update(view, loading = false, failed = true)
                    }
                }
                setOnScrollChangeListener { _, _, _, _, _ -> controller.update(this) }
                setDownloadListener { url, _, _, _, _ ->
                    controller.update(this, loading = false)
                    browserUrlOrNull(url)?.let { onOpenExternal(it.toString()) }
                }
                loadUrl(controller.initialUrl.toString())
            }
        },
        onRelease = { view ->
            controller.view = null
            view.stopLoading()
            view.setOnScrollChangeListener(null)
            view.setDownloadListener(null)
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        },
    )
}

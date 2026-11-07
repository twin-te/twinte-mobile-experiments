@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.twinte.mobile_experiments.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import io.ktor.http.Url
import kotlinx.cinterop.useContents
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLErrorCancelled
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKNavigationTypeLinkActivated
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject

internal actual class BrowserController internal constructor(internal val initialUrl: Url) {
    private var currentState by mutableStateOf(BrowserState(initialUrl))
    actual val state: BrowserState get() = currentState
    internal var view: WKWebView? = null
    // WKWebView keeps its delegates weakly referenced.
    internal var delegate: NSObject? = null

    actual val currentUrl: Url get() = view?.URL?.absoluteString?.let(::browserUrlOrNull) ?: state.url

    actual fun goBack(): Boolean {
        val view = view?.takeIf { it.canGoBack } ?: return false
        view.goBack()
        return true
    }
    actual fun reload() { view?.reload() }

    internal fun update(view: WKWebView, loading: Boolean = state.isLoading, failed: Boolean = state.failed) {
        currentState = BrowserState(
            url = view.URL?.absoluteString?.let(::browserUrlOrNull) ?: state.url,
            isLoading = loading, failed = failed,
            isAtTop = view.scrollView.contentOffset.useContents { y } <= -view.scrollView.adjustedContentInset.useContents { top },
        )
    }
}

@Composable
internal actual fun rememberBrowserController(url: Url): BrowserController = remember(url) { BrowserController(url) }

@Composable
internal actual fun BrowserView(controller: BrowserController, onOpenExternal: (String) -> Unit, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true,
        ),
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                defaultWebpagePreferences.allowsContentJavaScript = true
                preferences.javaScriptCanOpenWindowsAutomatically = false
            }
            WKWebView(CGRectMake(0.0, 0.0, 0.0, 0.0), configuration).apply {
                controller.view = this
                allowsBackForwardNavigationGestures = true
                val delegate = object : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol, UIScrollViewDelegateProtocol {
                    @ObjCSignatureOverride
                    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                        controller.update(webView, loading = true, failed = false)
                    }

                    @ObjCSignatureOverride
                    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                        controller.update(webView, loading = false)
                    }

                    @ObjCSignatureOverride
                    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
                        if (withError.code != NSURLErrorCancelled) controller.update(webView, loading = false, failed = true)
                    }

                    @ObjCSignatureOverride
                    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
                        if (withError.code != NSURLErrorCancelled) controller.update(webView, loading = false, failed = true)
                    }

                    override fun webView(
                        webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction,
                        decisionHandler: (WKNavigationActionPolicy) -> Unit,
                    ) {
                        val raw = decidePolicyForNavigationAction.request.URL?.absoluteString
                        val url = raw?.let(::browserUrlOrNull)
                        val clicked = decidePolicyForNavigationAction.navigationType == WKNavigationTypeLinkActivated
                        when {
                            decidePolicyForNavigationAction.targetFrame?.mainFrame == false && raw == "about:blank" ->
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                            url != null && clicked && isMapUrl(url) -> {
                                onOpenExternal(url.toString())
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                            }
                            url != null -> decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                            else -> {
                                if (clicked && decidePolicyForNavigationAction.request.URL?.scheme in listOf("mailto", "tel")) {
                                    raw?.let(onOpenExternal)
                                }
                                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                            }
                        }
                    }

                    override fun webView(
                        webView: WKWebView, decidePolicyForNavigationResponse: WKNavigationResponse,
                        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
                    ) {
                        val response = decidePolicyForNavigationResponse.response as? NSHTTPURLResponse
                        if (decidePolicyForNavigationResponse.forMainFrame && response != null && response.statusCode >= 400) {
                            controller.update(webView, loading = false, failed = true)
                        }
                        decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
                    }

                    override fun webView(
                        webView: WKWebView, createWebViewWithConfiguration: WKWebViewConfiguration,
                        forNavigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures,
                    ): WKWebView? {
                        val raw = forNavigationAction.request.URL?.absoluteString
                        if (forNavigationAction.targetFrame == null && raw?.let(::browserUrlOrNull) != null) {
                            webView.loadRequest(forNavigationAction.request)
                        }
                        return null
                    }

                    override fun webView(
                        webView: WKWebView, runJavaScriptAlertPanelWithMessage: String,
                        initiatedByFrame: WKFrameInfo, completionHandler: () -> Unit,
                    ) {
                        webView.presentScriptDialog(runJavaScriptAlertPanelWithMessage, initiatedByFrame.securityOrigin.host, confirm = false) {
                            completionHandler()
                        }
                    }

                    override fun webView(
                        webView: WKWebView, runJavaScriptConfirmPanelWithMessage: String,
                        initiatedByFrame: WKFrameInfo, completionHandler: (Boolean) -> Unit,
                    ) {
                        webView.presentScriptDialog(
                            runJavaScriptConfirmPanelWithMessage, initiatedByFrame.securityOrigin.host, confirm = true, completionHandler,
                        )
                    }

                    override fun scrollViewDidScroll(scrollView: UIScrollView) { controller.update(this@apply) }
                }
                controller.delegate = delegate
                navigationDelegate = delegate
                UIDelegate = delegate
                scrollView.delegate = delegate
                loadRequest(NSURLRequest(NSURL(string = controller.initialUrl.toString())))
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.navigationDelegate = null
            view.UIDelegate = null
            view.scrollView.delegate = null
            controller.view = null
            controller.delegate = null
        },
    )
}

private fun WKWebView.presentScriptDialog(message: String, origin: String, confirm: Boolean, completion: (Boolean) -> Unit) {
    var presenter = window?.rootViewController
    while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
    if (presenter == null) {
        completion(false)
        return
    }
    val alert = UIAlertController.alertControllerWithTitle(origin, message, UIAlertControllerStyleAlert)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault) { completion(true) })
    if (confirm) alert.addAction(UIAlertAction.actionWithTitle("キャンセル", UIAlertActionStyleCancel) { completion(false) })
    presenter.presentViewController(alert, animated = true, completion = null)
}

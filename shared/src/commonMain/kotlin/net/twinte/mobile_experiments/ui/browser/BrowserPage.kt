package net.twinte.mobile_experiments.ui.browser

import androidx.compose.runtime.saveable.listSaver
import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal data class BrowserPage(val title: String, val url: Url)

internal val browserPageSaver = listSaver<BrowserPage?, String>(
    save = { page -> page?.let { listOf(it.title, it.url.toString()) }.orEmpty() },
    restore = { values ->
        if (values.size == 2) browserUrlOrNull(values[1])?.let { BrowserPage(values[0], it) } else null
    },
)

internal fun browserUrlOrNull(value: String): Url? = runCatching { Url(value) }.getOrNull()?.takeIf {
    it.protocol == URLProtocol.HTTPS && it.host.isNotBlank() && it.user == null && it.password == null
}

internal fun isMapUrl(url: Url): Boolean =
    url.protocol == URLProtocol.HTTPS && url.host == "www.google.com" &&
        (url.encodedPath == "/maps" || url.encodedPath.startsWith("/maps/"))

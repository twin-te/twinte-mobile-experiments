package net.twinte.mobile_experiments.core.api.ktor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies

@Composable
fun rememberTwinteHttpClient(): HttpClient {
    val httpClient = remember {
        HttpClient {
            followRedirects = false
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }
        }
    }
    DisposableEffect(httpClient) {
        onDispose {
            httpClient.close()
        }
    }
    return httpClient
}

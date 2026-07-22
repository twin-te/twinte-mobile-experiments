package net.twinte.mobile_experiments.core.api.ktor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient

@Composable
fun rememberTwinteHttpClient(): HttpClient {
    return rememberTwinteHttpClient(followRedirects = true)
}

@Composable
fun rememberTwinteLoginHttpClient(): HttpClient {
    return rememberTwinteHttpClient(followRedirects = false)
}

@Composable
private fun rememberTwinteHttpClient(followRedirects: Boolean): HttpClient {
    val httpClient = remember {
        HttpClient {
            this.followRedirects = followRedirects
        }
    }
    DisposableEffect(httpClient) {
        onDispose {
            httpClient.close()
        }
    }
    return httpClient
}

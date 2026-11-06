package net.twinte.mobile_experiments.core.api.ktor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

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
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
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

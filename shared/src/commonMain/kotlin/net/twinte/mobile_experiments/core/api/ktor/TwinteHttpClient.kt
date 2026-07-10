package net.twinte.mobile_experiments.core.api.ktor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient

@Composable
fun rememberTwinteHttpClient(): HttpClient {
    val httpClient = remember {
        HttpClient()
    }
    DisposableEffect(httpClient) {
        onDispose {
            httpClient.close()
        }
    }
    return httpClient
}

@Composable
fun rememberTwinteLoginHttpClient(): HttpClient {
    val httpClient = remember {
        HttpClient {
            followRedirects = false
        }
    }
    DisposableEffect(httpClient) {
        onDispose {
            httpClient.close()
        }
    }
    return httpClient
}

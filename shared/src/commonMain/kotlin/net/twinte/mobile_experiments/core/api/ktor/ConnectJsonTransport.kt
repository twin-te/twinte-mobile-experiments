package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.TwinteSession

internal class ConnectJsonTransport(
    private val apiBaseUrl: Url,
    private val httpClient: HttpClient,
) {
    suspend fun post(
        service: String,
        method: String,
        session: TwinteSession,
        requestBody: String,
    ): String = mapKtorNetworkErrors {
        val response = httpClient.post(
            URLBuilder(apiBaseUrl).apply {
                appendPathSegments(service, method)
            }.build(),
        ) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            header(ConnectProtocolVersionHeader, "1")
            header(HttpHeaders.Cookie, "${session.cookieName}=${session.sessionId}")
            setBody(requestBody)
        }
        val body = response.body<String>()
        if (!response.status.isSuccess()) {
            throw TwinteApiException(response.status.value, body)
        }
        body
    }
}

internal const val ConnectProtocolVersionHeader = "Connect-Protocol-Version"

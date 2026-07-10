package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import io.ktor.http.parseServerSetCookieHeader
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.AuthChallenge
import net.twinte.mobile_experiments.core.auth.TwinteSession

internal suspend fun createSessionWithIdToken(
    appBaseUrl: Url,
    httpClient: HttpClient,
    providerPath: String,
    idToken: String,
    challengeId: String,
    authorizationCode: String? = null,
    currentSession: TwinteSession? = null,
): TwinteSession {
    val response = httpClient.post("$appBaseUrl/auth/v4/$providerPath/idToken") {
        currentSession?.let {
            header(HttpHeaders.Cookie, "${it.cookieName}=${it.sessionId}")
        }
        setBody(
            FormDataContent(
                Parameters.build {
                    append("token", idToken)
                    append("challenge_id", challengeId)
                    authorizationCode?.let { append("authorization_code", it) }
                    append("redirect_url", appBaseUrl.toString())
                },
            ),
        )
    }
    if (response.status != HttpStatusCode.Found) {
        val body = response.body<String>()
        throw TwinteApiException(response.status, body.ifBlank { "Session creation failed" })
    }
    response.headers.getAll(HttpHeaders.SetCookie)
        .orEmpty()
        .mapNotNull { header ->
            runCatching {
                parseServerSetCookieHeader(header)
            }.getOrNull()
        }
        .firstOrNull { it.name == SessionCookieName }
        ?.value
        ?.takeIf { it.isNotEmpty() }
        ?.let { return TwinteSession(sessionId = it) }
    throw TwinteApiException(response.status, "Session cookie is missing")
}

internal suspend fun createAuthChallenge(
    appBaseUrl: Url,
    httpClient: HttpClient,
    providerPath: String,
): AuthChallenge {
    val response = httpClient.post("$appBaseUrl/auth/v4/$providerPath/challenge")
    val body = response.body<String>()
    if (!response.status.isSuccess()) {
        throw TwinteApiException(response.status, body.ifBlank { "Challenge creation failed" })
    }
    val values = parseQueryString(body)
    val challengeId = values["challenge_id"]?.takeIf(String::isNotBlank)
        ?: throw TwinteApiException(response.status, "challenge_id is missing")
    val nonce = values["nonce"]?.takeIf(String::isNotBlank)
        ?: throw TwinteApiException(response.status, "nonce is missing")
    return AuthChallenge(id = challengeId, nonce = nonce)
}

private const val SessionCookieName = "twinte_session"

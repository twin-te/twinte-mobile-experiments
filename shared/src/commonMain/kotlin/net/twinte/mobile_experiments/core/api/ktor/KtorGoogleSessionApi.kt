package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.GoogleSessionApi
import net.twinte.mobile_experiments.core.auth.TwinteSession

class KtorGoogleSessionApi(
    private val appBaseUrl: Url = Url("https://app.twinte.net"),
    private val httpClient: HttpClient = HttpClient {
        followRedirects = false
    },
) : GoogleSessionApi {
    constructor(
        appBaseUrl: String,
        httpClient: HttpClient = HttpClient {
            followRedirects = false
        },
    ) : this(Url(appBaseUrl.trimEnd('/')), httpClient)

    override suspend fun createSessionWithIdToken(idToken: String): TwinteSession {
        val response = httpClient.get("${appBaseUrl}/auth/v4/google/idToken") {
            parameter("token", idToken)
            parameter("redirect_url", appBaseUrl.toString())
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

    private companion object {
        const val SessionCookieName = "twinte_session"
    }
}

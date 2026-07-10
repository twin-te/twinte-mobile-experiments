package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.http.Url
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

    override suspend fun createSessionWithIdToken(idToken: String, currentSession: TwinteSession?): TwinteSession =
        createSessionWithIdToken(
            appBaseUrl = appBaseUrl,
            httpClient = httpClient,
            providerPath = "google",
            idToken = idToken,
            currentSession = currentSession,
        )
}

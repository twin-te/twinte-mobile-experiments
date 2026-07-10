package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.http.Url
import net.twinte.mobile_experiments.core.auth.GoogleSessionApi
import net.twinte.mobile_experiments.core.auth.AuthChallenge
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

    override suspend fun createChallenge(): AuthChallenge =
        createAuthChallenge(appBaseUrl, httpClient, "google")

    override suspend fun createSessionWithIdToken(
        idToken: String,
        challengeId: String,
        currentSession: TwinteSession?,
    ): TwinteSession =
        createSessionWithIdToken(
            appBaseUrl = appBaseUrl,
            httpClient = httpClient,
            providerPath = "google",
            idToken = idToken,
            challengeId = challengeId,
            currentSession = currentSession,
        )
}

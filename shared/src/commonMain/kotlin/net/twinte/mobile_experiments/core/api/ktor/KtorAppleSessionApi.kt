package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.http.Url
import net.twinte.mobile_experiments.core.auth.AppleSessionApi
import net.twinte.mobile_experiments.core.auth.AppleSignInCredential
import net.twinte.mobile_experiments.core.auth.AuthChallenge
import net.twinte.mobile_experiments.core.auth.TwinteSession

class KtorAppleSessionApi(
    private val appBaseUrl: Url = Url("https://app.twinte.net"),
    private val httpClient: HttpClient = HttpClient {
        followRedirects = false
    },
) : AppleSessionApi {
    constructor(
        appBaseUrl: String,
        httpClient: HttpClient = HttpClient {
            followRedirects = false
        },
    ) : this(Url(appBaseUrl.trimEnd('/')), httpClient)

    override suspend fun createChallenge(): AuthChallenge =
        createAuthChallenge(appBaseUrl, httpClient, "apple")

    override suspend fun createSessionWithCredential(
        credential: AppleSignInCredential,
        challengeId: String,
        currentSession: TwinteSession?,
    ): TwinteSession =
        createSessionWithIdToken(
            appBaseUrl = appBaseUrl,
            httpClient = httpClient,
            providerPath = "apple",
            idToken = credential.idToken,
            challengeId = challengeId,
            authorizationCode = credential.authorizationCode,
            currentSession = currentSession,
        )
}

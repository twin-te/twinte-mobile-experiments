package net.twinte.mobile_experiments.core.auth

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import net.twinte.mobile_experiments.core.domain.AuthProvider

class LoginUrlBuilder(
    private val appBaseUrl: Url,
) {
    constructor(appBaseUrl: String) : this(Url(appBaseUrl.trimEnd('/')))

    fun loginUrl(provider: AuthProvider, redirectUrl: Url = appBaseUrl): Url {
        val providerPath = when (provider) {
            AuthProvider.Google -> "google"
            AuthProvider.Apple -> "apple"
            AuthProvider.Twitter -> "twitter"
        }
        return URLBuilder(appBaseUrl).apply {
            appendPathSegments("auth", "v4", providerPath)
            parameters.append("redirect_url", redirectUrl.toString())
        }.build()
    }

    fun logoutUrl(redirectUrl: Url = appBaseUrl): Url =
        URLBuilder(appBaseUrl).apply {
            appendPathSegments("auth", "v4", "logout")
            parameters.append("redirect_url", redirectUrl.toString())
        }.build()
}

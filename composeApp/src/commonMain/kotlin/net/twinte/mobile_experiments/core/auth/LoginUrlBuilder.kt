package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.AuthProvider

class LoginUrlBuilder(
    private val appBaseUrl: String,
) {
    fun loginUrl(provider: AuthProvider, redirectUrl: String = appBaseUrl): String {
        val providerPath = when (provider) {
            AuthProvider.Google -> "google"
            AuthProvider.Apple -> "apple"
            AuthProvider.Twitter -> "twitter"
        }
        return "$appBaseUrl/auth/v4/$providerPath?redirect_url=$redirectUrl"
    }

    fun logoutUrl(redirectUrl: String = appBaseUrl): String =
        "$appBaseUrl/auth/v4/logout?redirect_url=$redirectUrl"
}

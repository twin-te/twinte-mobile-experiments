package net.twinte.mobile_experiments.core.auth

import io.ktor.http.Url
import net.twinte.mobile_experiments.core.domain.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginUrlBuilderTest {
    @Test
    fun buildsProviderLoginUrl() {
        val builder = LoginUrlBuilder("https://app.twinte.net")

        assertEquals(
            "https://app.twinte.net/auth/v4/google?redirect_url=https%3A%2F%2Fapp.twinte.net",
            builder.loginUrl(AuthProvider.Google).toString(),
        )
    }

    @Test
    fun encodesRedirectUrl() {
        val builder = LoginUrlBuilder("https://app.twinte.net/")

        assertEquals(
            "https://app.twinte.net/auth/v4/apple?redirect_url=https%3A%2F%2Fapp.twinte.net%2Fcourse%2Fabc%3Ftab%3Dmemo",
            builder.loginUrl(AuthProvider.Apple, Url("https://app.twinte.net/course/abc?tab=memo")).toString(),
        )
    }

    @Test
    fun buildsLogoutUrl() {
        val builder = LoginUrlBuilder("https://app.twinte.net")

        assertEquals(
            "https://app.twinte.net/auth/v4/logout?redirect_url=https%3A%2F%2Fapp.twinte.net",
            builder.logoutUrl().toString(),
        )
    }
}

package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginUrlBuilderTest {
    @Test
    fun buildsProviderLoginUrl() {
        val builder = LoginUrlBuilder("https://app.twinte.net")

        assertEquals(
            "https://app.twinte.net/auth/v4/google?redirect_url=https://app.twinte.net",
            builder.loginUrl(AuthProvider.Google),
        )
    }
}

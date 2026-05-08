package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import platform.Foundation.NSProcessInfo

class KtorAuthApiIntegrationTest {
    @Test
    fun getMeAgainstConfiguredEndpoint() = runTest {
        if (env("TWINTE_KTOR_AUTH_INTEGRATION") != "true") return@runTest

        val sessionId = env("TWINTE_SESSION_ID")
        val api = KtorAuthApi(
            apiBaseUrl = env("TWINTE_API_URL") ?: "https://app.twinte.net/api/v4",
            session = sessionId?.let {
                TwinteSession(
                    sessionId = it,
                    cookieName = env("TWINTE_SESSION_COOKIE_NAME") ?: "twinte_session",
                )
            },
        )

        if (sessionId == null) {
            val error = assertFailsWith<KtorApiException> {
                api.getMe()
            }
            assertEquals(HttpStatusCode.Unauthorized, error.status)
            return@runTest
        }

        val user = api.getMe()
        assertTrue(user.id.isNotBlank())
    }
}

private fun env(name: String): String? =
    NSProcessInfo.processInfo.environment[name] as? String

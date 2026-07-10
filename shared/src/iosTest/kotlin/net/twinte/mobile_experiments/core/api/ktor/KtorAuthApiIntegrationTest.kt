package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.TwinteSession
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
        )

        if (sessionId == null) {
            val error = assertFailsWith<TwinteApiException> {
                api.getMe(TwinteSession("missing-session-id"))
            }
            assertEquals(HttpStatusCode.Unauthorized, error.status)
            return@runTest
        }

        val user = api.getMe(
            TwinteSession(
                sessionId = sessionId,
                cookieName = env("TWINTE_SESSION_COOKIE_NAME") ?: "twinte_session",
            ),
        )
        assertTrue(user.id.isNotBlank())
    }
}

private fun env(name: String): String? =
    NSProcessInfo.processInfo.environment[name] as? String

package net.twinte.mobile_experiments.core.api.grpc

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GrpcAuthApiIntegrationTest {
    @Test
    fun getMeAgainstConfiguredEndpoint() = runTest {
        if (System.getenv("TWINTE_GRPC_AUTH_INTEGRATION") != "true") return@runTest

        val sessionId = requireNotNull(System.getenv("TWINTE_SESSION_ID")) {
            "TWINTE_SESSION_ID is required when TWINTE_GRPC_AUTH_INTEGRATION=true"
        }
        val endpoint = GrpcEndpoint(
            host = System.getenv("TWINTE_GRPC_HOST") ?: "app.twinte.net",
            port = System.getenv("TWINTE_GRPC_PORT")?.toInt() ?: 443,
            usePlaintext = System.getenv("TWINTE_GRPC_PLAINTEXT") == "true",
            sessionId = sessionId,
            sessionCookieName = System.getenv("TWINTE_SESSION_COOKIE_NAME") ?: "twinte_session",
        )

        val user = createGrpcAuthApi(endpoint).getMe()

        assertTrue(user.id.isNotBlank())
    }
}

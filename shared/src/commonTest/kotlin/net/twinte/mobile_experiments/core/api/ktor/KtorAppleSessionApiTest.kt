package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.auth.AppleSignInCredential
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorAppleSessionApiTest {
    @Test
    fun createSessionWithCredentialPostsToAppleIdTokenEndpoint() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/auth/v4/apple/idToken", request.url.encodedPath)
            assertEquals(null, request.url.parameters["token"])
            assertEquals(null, request.url.parameters["authorization_code"])
            assertEquals(null, request.url.parameters["redirect_url"])
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    HttpHeaders.SetCookie,
                    "twinte_session=apple-session-id; Path=/; HttpOnly",
                ),
            )
        }
        val api = KtorAppleSessionApi(httpClient = HttpClient(engine))

        val session = api.createSessionWithCredential(
            AppleSignInCredential(
                idToken = "apple-id-token",
                authorizationCode = "apple-code",
            ),
            challengeId = "challenge-id",
        )

        assertEquals("apple-session-id", session.sessionId)
    }
}

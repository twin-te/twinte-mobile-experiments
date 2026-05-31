package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.TwinteApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorGoogleSessionApiTest {
    @Test
    fun createSessionWithIdTokenReadsSessionCookieFromCurrentResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/auth/v4/google/idToken", request.url.encodedPath)
            assertEquals(null, request.url.parameters["token"])
            assertEquals(null, request.url.parameters["redirect_url"])
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    HttpHeaders.SetCookie,
                    "twinte_session=session-id; Path=/; HttpOnly",
                ),
            )
        }
        val api = KtorGoogleSessionApi(httpClient = HttpClient(engine))

        val session = api.createSessionWithIdToken("id-token")

        assertEquals("session-id", session.sessionId)
    }

    @Test
    fun createSessionWithIdTokenIgnoresStoredCookiesWhenResponseHasNoSessionCookie() = runTest {
        val api = KtorGoogleSessionApi(
            httpClient = HttpClient(
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/auth/v4/google/idToken" -> respond(
                            content = "",
                            status = HttpStatusCode.Found,
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                },
            ) {
                install(HttpCookies) {
                    default {
                        addCookie(
                            Url("https://app.twinte.net"),
                            Cookie("twinte_session", "old-session-id"),
                        )
                    }
                }
            },
        )

        assertFailsWith<TwinteApiException> {
            api.createSessionWithIdToken("id-token")
        }
    }
}

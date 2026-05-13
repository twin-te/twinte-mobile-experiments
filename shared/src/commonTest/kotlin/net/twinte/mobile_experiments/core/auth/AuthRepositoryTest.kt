package net.twinte.mobile_experiments.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.twinte.api.auth.v1.GetMeResponse
import net.twinte.api.auth.v1.Provider
import net.twinte.api.auth.v1.User as ApiUser
import net.twinte.api.auth.v1.UserAuthentication as ApiUserAuthentication
import net.twinte.api.shared.UUID
import net.twinte.mobile_experiments.core.api.ktor.KtorApiException
import pbandk.ExperimentalProtoJson
import pbandk.json.encodeToJsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalProtoJson::class)
class AuthRepositoryTest {
    @Test
    fun signInWithGoogleIdTokenSavesSessionAndFetchesUser() = runTest {
        val store = MemorySessionStore()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth/v4/google/idToken" -> {
                    assertEquals("id-token", request.url.parameters["token"])
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(
                            HttpHeaders.SetCookie,
                            "twinte_session=session-id; Path=/; HttpOnly",
                        ),
                    )
                }
                "/api/v4/auth.v1.AuthService/GetMe" -> {
                    assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
                    respond(
                        content = getMeResponseJson(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val repository = AuthRepository(
            sessionStore = store,
            httpClient = HttpClient(engine),
        )

        val session = repository.signInWithGoogleIdToken("id-token")

        assertEquals("session-id", session.sessionId)
        assertEquals("user-id", session.user.id)
        assertEquals("session-id", store.getSessionId())
    }

    @Test
    fun signInWithGoogleIdTokenIgnoresStoredCookiesWhenResponseHasNoSessionCookie() = runTest {
        val store = MemorySessionStore()
        val repository = AuthRepository(
            sessionStore = store,
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

        assertFailsWith<KtorApiException> {
            repository.signInWithGoogleIdToken("id-token")
        }
        assertNull(store.getSessionId())
    }

    @Test
    fun restoreSessionClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val repository = AuthRepository(
            sessionStore = store,
            httpClient = HttpClient(
                MockEngine {
                    respond(
                        content = "unauthorized",
                        status = HttpStatusCode.Unauthorized,
                    )
                },
            ),
        )

        assertFailsWith<KtorApiException> {
            repository.restoreSession()
        }
        assertNull(store.getSessionId())
    }

    private fun getMeResponseJson(): String =
        GetMeResponse(
            user = ApiUser(
                id = UUID("user-id"),
                authentications = listOf(
                    ApiUserAuthentication(
                        provider = Provider.GOOGLE,
                        socialId = "google-id",
                    ),
                ),
            ),
        ).encodeToJsonString()
}

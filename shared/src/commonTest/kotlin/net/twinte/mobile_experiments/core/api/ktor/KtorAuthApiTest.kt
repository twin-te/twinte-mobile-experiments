package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import net.twinte.api.auth.v1.GetMeResponse
import net.twinte.api.auth.v1.Provider
import net.twinte.api.auth.v1.User as ApiUser
import net.twinte.api.auth.v1.UserAuthentication as ApiUserAuthentication
import net.twinte.api.shared.UUID
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.AuthProvider
import pbandk.ExperimentalProtoJson
import pbandk.json.encodeToJsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalProtoJson::class)
class KtorAuthApiTest {
    @Test
    fun getMePostsToConnectEndpointWithSessionCookie() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/auth.v1.AuthService/GetMe",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            respond(
                content = GetMeResponse(
                    user = ApiUser(
                        id = UUID("user-id"),
                        authentications = listOf(
                            ApiUserAuthentication(
                                provider = Provider.GOOGLE,
                                socialId = "google-id",
                            ),
                        ),
                    ),
                ).encodeToJsonString(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(
            httpClient = HttpClient(engine),
        )

        val user = api.getMe(TwinteSession("session-id"))

        assertEquals("user-id", user.id)
        assertEquals(AuthProvider.Google, user.authentications.single().provider)
        assertEquals("google-id", user.authentications.single().socialId)
    }

    @Test
    fun logoutGetsLogoutEndpointWithSessionCookie() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(
                "https://app.twinte.net/auth/v4/logout?redirect_url=https%3A%2F%2Fapp.twinte.net",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            respond(
                content = "",
                status = HttpStatusCode.Found,
            )
        }
        val api = KtorAuthApi(
            httpClient = HttpClient(engine),
        )

        api.logout(TwinteSession("session-id"))
    }

    @Test
    fun deleteUserAuthenticationPostsToConnectEndpointWithProvider() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/auth.v1.AuthService/DeleteUserAuthentication",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            assertTrue((request.body as TextContent).text.contains("PROVIDER_APPLE"))
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(
            httpClient = HttpClient(engine),
        )

        api.deleteUserAuthentication(TwinteSession("session-id"), AuthProvider.Apple)
    }

    @Test
    fun deleteAccountPostsToConnectEndpointWithSessionCookie() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/auth.v1.AuthService/DeleteAccount",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(
            httpClient = HttpClient(engine),
        )

        api.deleteAccount(TwinteSession("session-id"))
    }
}

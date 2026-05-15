package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
}

package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import net.twinte.api.auth.v1.GetMeRequest
import net.twinte.api.auth.v1.GetMeResponse
import net.twinte.api.auth.v1.Provider as ApiProvider
import net.twinte.api.auth.v1.User as ApiUser
import net.twinte.api.auth.v1.UserAuthentication as ApiUserAuthentication
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import pbandk.ExperimentalProtoJson
import pbandk.json.decodeFromJsonString
import pbandk.json.encodeToJsonString

@OptIn(ExperimentalProtoJson::class)
class KtorAuthApi(
    private val apiBaseUrl: Url = Url("https://app.twinte.net/api/v4"),
    private val session: TwinteSession? = null,
    private val httpClient: HttpClient = HttpClient(),
) : AuthApi {
    constructor(
        apiBaseUrl: String,
        session: TwinteSession? = null,
        httpClient: HttpClient = HttpClient(),
    ) : this(Url(apiBaseUrl.trimEnd('/')), session, httpClient)

    override suspend fun getMe(): User {
        val response = httpClient.post(connectUrl("auth.v1.AuthService", "GetMe")) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            header("Connect-Protocol-Version", "1")
            session?.let {
                header(HttpHeaders.Cookie, "${it.cookieName}=${it.sessionId}")
            }
            setBody(GetMeRequest().encodeToJsonString())
        }
        val body = response.body<String>()
        if (!response.status.isSuccess()) {
            throw KtorApiException(response.status, body)
        }
        return GetMeResponse.decodeFromJsonString(body).toDomainUser()
    }

    private fun connectUrl(service: String, method: String): Url =
        URLBuilder(apiBaseUrl).apply {
            appendPathSegments(service, method)
        }.build()
}

data class TwinteSession(
    val sessionId: String,
    val cookieName: String = "twinte_session",
)

class KtorApiException(
    val status: HttpStatusCode,
    val responseBody: String,
) : RuntimeException("Twinte API request failed: $status $responseBody")

private fun GetMeResponse.toDomainUser(): User {
    val user = requireNotNull(user) { "GetMeResponse.user is missing" }
    return user.toDomainUser()
}

private fun ApiUser.toDomainUser(): User =
    User(
        id = requireNotNull(id) { "User.id is missing" }.value,
        authentications = authentications.map { it.toDomainUserAuthentication() },
    )

private fun ApiUserAuthentication.toDomainUserAuthentication(): UserAuthentication =
    UserAuthentication(
        provider = provider.toAuthProvider(),
        socialId = socialId,
    )

private fun ApiProvider.toAuthProvider(): AuthProvider =
    when (this) {
        ApiProvider.GOOGLE -> AuthProvider.Google
        ApiProvider.APPLE -> AuthProvider.Apple
        ApiProvider.TWITTER -> AuthProvider.Twitter
        else -> error("Unsupported authentication provider: $this")
    }

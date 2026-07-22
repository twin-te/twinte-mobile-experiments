package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
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
import net.twinte.api.auth.v1.DeleteAccountRequest
import net.twinte.api.auth.v1.DeleteUserAuthenticationRequest
import net.twinte.api.auth.v1.GetMeRequest
import net.twinte.api.auth.v1.GetMeResponse
import net.twinte.api.auth.v1.Provider as ApiProvider
import net.twinte.api.auth.v1.User as ApiUser
import net.twinte.api.auth.v1.UserAuthentication as ApiUserAuthentication
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import pbandk.ExperimentalProtoJson
import pbandk.json.decodeFromJsonString
import pbandk.json.encodeToJsonString

@OptIn(ExperimentalProtoJson::class)
class KtorAuthApi(
    private val apiBaseUrl: Url = Url("https://app.twinte.net/api/v4"),
    private val appBaseUrl: Url = Url("https://app.twinte.net"),
    private val httpClient: HttpClient,
) : AuthApi {
    constructor(
        apiBaseUrl: String,
        appBaseUrl: String = apiBaseUrl.removeSuffix("/api/v4"),
        httpClient: HttpClient,
    ) : this(
        apiBaseUrl = Url(apiBaseUrl.trimEnd('/')),
        appBaseUrl = Url(appBaseUrl.trimEnd('/')),
        httpClient = httpClient,
    )

    override suspend fun getMe(session: TwinteSession): User {
        val body = postConnectJson(
            session = session,
            method = "GetMe",
            requestBody = GetMeRequest().encodeToJsonString(),
        )
        return GetMeResponse.decodeFromJsonString(body).toDomainUser()
    }

    override suspend fun logout(session: TwinteSession) {
        val response = mapKtorNetworkErrors {
            httpClient.get(authUrl("logout")) {
                header(HttpHeaders.Cookie, "${session.cookieName}=${session.sessionId}")
                url {
                    parameters.append("redirect_url", appBaseUrl.toString())
                }
            }
        }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Found) {
            throw TwinteApiException(response.status.value, response.body<String>())
        }
    }

    override suspend fun deleteUserAuthentication(session: TwinteSession, provider: AuthProvider) {
        postConnectJson(
            session = session,
            method = "DeleteUserAuthentication",
            requestBody = DeleteUserAuthenticationRequest(provider = provider.toApiProvider()).encodeToJsonString(),
        )
    }

    override suspend fun deleteAccount(session: TwinteSession) {
        postConnectJson(
            session = session,
            method = "DeleteAccount",
            requestBody = DeleteAccountRequest().encodeToJsonString(),
        )
    }

    private suspend fun postConnectJson(
        session: TwinteSession,
        method: String,
        requestBody: String,
    ): String {
        val response = mapKtorNetworkErrors {
            httpClient.post(connectUrl("auth.v1.AuthService", method)) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header("Connect-Protocol-Version", "1")
                header(HttpHeaders.Cookie, "${session.cookieName}=${session.sessionId}")
                setBody(requestBody)
            }
        }
        val body = response.body<String>()
        if (!response.status.isSuccess()) {
            throw TwinteApiException(response.status.value, body)
        }
        return body
    }

    private fun connectUrl(service: String, method: String): Url =
        URLBuilder(apiBaseUrl).apply {
            appendPathSegments(service, method)
        }.build()

    private fun authUrl(method: String): Url =
        URLBuilder(appBaseUrl).apply {
            appendPathSegments("auth", "v4", method)
        }.build()
}

typealias KtorApiException = TwinteApiException

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

private fun AuthProvider.toApiProvider(): ApiProvider =
    when (this) {
        AuthProvider.Google -> ApiProvider.GOOGLE
        AuthProvider.Apple -> ApiProvider.APPLE
        AuthProvider.Twitter -> ApiProvider.TWITTER
    }

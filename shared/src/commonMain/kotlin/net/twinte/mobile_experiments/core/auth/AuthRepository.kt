package net.twinte.mobile_experiments.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.plugins.cookies.get
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import net.twinte.mobile_experiments.core.api.ktor.KtorApiException
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.TwinteSession
import net.twinte.mobile_experiments.core.domain.User

class AuthRepository(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
    private val appBaseUrl: Url = Url("https://app.twinte.net"),
    private val apiBaseUrl: Url = Url("https://app.twinte.net/api/v4"),
) {
    suspend fun restoreSession(): AuthSession? {
        val sessionId = sessionStore.getSessionId() ?: return null
        return runCatching {
            AuthSession(sessionId, getMe(sessionId))
        }.onFailure { error ->
            if (error is KtorApiException && error.status == HttpStatusCode.Unauthorized) {
                sessionStore.clearSessionId()
            }
        }.getOrThrow()
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthSession {
        val sessionId = fetchSessionIdWithGoogleIdToken(idToken)
        sessionStore.saveSessionId(sessionId)
        return AuthSession(sessionId, getMe(sessionId))
    }

    suspend fun getMe(): User {
        val sessionId = requireNotNull(sessionStore.getSessionId()) { "No session" }
        return getMe(sessionId)
    }

    suspend fun clearSession() {
        sessionStore.clearSessionId()
    }

    private suspend fun getMe(sessionId: String): User =
        KtorAuthApi(
            apiBaseUrl = apiBaseUrl,
            session = TwinteSession(sessionId),
            httpClient = httpClient,
        ).getMe()

    private suspend fun fetchSessionIdWithGoogleIdToken(idToken: String): String {
        val response = httpClient.get("${appBaseUrl}/auth/v4/google/idToken") {
            parameter("token", idToken)
            parameter("redirect_url", appBaseUrl.toString())
        }
        httpClient.cookies(appBaseUrl)[SessionCookieName]?.value?.let { return it }
        throw KtorApiException(response.status, "Session cookie is missing")
    }
}

data class AuthSession(
    val sessionId: String,
    val user: User,
)

private const val SessionCookieName = "twinte_session"

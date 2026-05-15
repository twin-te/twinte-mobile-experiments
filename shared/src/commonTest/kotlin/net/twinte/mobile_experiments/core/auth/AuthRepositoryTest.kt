package net.twinte.mobile_experiments.core.auth

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthRepositoryTest {
    @Test
    fun signInWithGoogleIdTokenSavesSessionAndFetchesUser() = runTest {
        val store = MemorySessionStore()
        val authApi = FakeAuthApi()
        val googleSessionApi = FakeGoogleSessionApi(TwinteSession("session-id"))
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = googleSessionApi,
        )

        val session = repository.signInWithGoogleIdToken("id-token")

        assertEquals("session-id", session.sessionId)
        assertEquals("user-id", session.user.id)
        assertEquals("session-id", store.getSessionId())
        assertEquals("id-token", googleSessionApi.requestedIdToken)
        assertEquals(TwinteSession("session-id"), authApi.lastSession)
    }

    @Test
    fun restoreSessionClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val authApi = FakeAuthApi(
            getMeResult = Result.failure(TwinteApiException(HttpStatusCode.Unauthorized, "unauthorized")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
        )

        assertFailsWith<TwinteApiException> {
            repository.restoreSession()
        }
        assertNull(store.getSessionId())
    }

    private class FakeAuthApi(
        private val getMeResult: Result<User> = Result.success(
            User(
                id = "user-id",
                authentications = listOf(
                    UserAuthentication(
                        provider = AuthProvider.Google,
                        socialId = "google-id",
                    ),
                ),
            ),
        ),
    ) : AuthApi {
        var lastSession: TwinteSession? = null
            private set

        override suspend fun getMe(session: TwinteSession): User {
            lastSession = session
            return getMeResult.getOrThrow()
        }
    }

    private class FakeGoogleSessionApi(
        private val session: TwinteSession,
    ) : GoogleSessionApi {
        var requestedIdToken: String? = null
            private set

        override suspend fun createSessionWithIdToken(idToken: String): TwinteSession {
            requestedIdToken = idToken
            return session
        }
    }
}

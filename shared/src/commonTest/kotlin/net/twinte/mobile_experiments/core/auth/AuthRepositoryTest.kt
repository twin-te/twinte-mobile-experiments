package net.twinte.mobile_experiments.core.auth

import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthRepositoryTest {
    @Test
    fun signInWithGoogleIdTokenSavesSessionAndFetchesUser() = runTest {
        val store = MemorySessionStore()
        val authApi = FakeAuthApi()
        val googleSessionApi = FakeGoogleSessionApi(TwinteSession("session-id"))
        val appleSessionApi = FakeAppleSessionApi(TwinteSession("unused"))
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = googleSessionApi,
            appleSessionApi = appleSessionApi,
        )

        val session = repository.signInWithGoogleIdToken("id-token")

        assertEquals("session-id", session.sessionId)
        assertEquals("user-id", session.user.id)
        assertEquals("session-id", store.getSessionId())
        assertEquals("id-token", googleSessionApi.requestedIdToken)
        assertEquals(TwinteSession("session-id"), authApi.lastSession)
    }

    @Test
    fun signInWithAppleCredentialSavesSessionAndFetchesUser() = runTest {
        val store = MemorySessionStore()
        val authApi = FakeAuthApi()
        val appleSessionApi = FakeAppleSessionApi(TwinteSession("apple-session-id"))
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = appleSessionApi,
        )

        val session = repository.signInWithAppleCredential(
            AppleSignInCredential(
                idToken = "apple-id-token",
                authorizationCode = "apple-code",
            ),
        )

        assertEquals("apple-session-id", session.sessionId)
        assertEquals("user-id", session.user.id)
        assertEquals("apple-session-id", store.getSessionId())
        assertEquals(
            AppleSignInCredential(
                idToken = "apple-id-token",
                authorizationCode = "apple-code",
            ),
            appleSessionApi.requestedCredential,
        )
        assertEquals(TwinteSession("apple-session-id"), authApi.lastSession)
    }

    @Test
    fun signInKeepsPreviousSessionWhenNewSessionValidationFails() = runTest {
        val store = MemorySessionStore("previous-session-id")
        val authApi = FakeAuthApi(
            getMeResult = Result.failure(TwinteApiException(500, "failed")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("new-session-id")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        assertFailsWith<TwinteApiException> {
            repository.signInWithGoogleIdToken("id-token")
        }

        assertEquals("previous-session-id", store.getSessionId())
    }

    @Test
    fun restoreSessionReturnsNullAndClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val authApi = FakeAuthApi(
            getMeResult = Result.failure(TwinteApiException(401, "unauthorized")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        assertNull(repository.restoreSession())
        assertNull(store.getSessionId())
    }

    @Test
    fun getMeClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val authApi = FakeAuthApi(
            getMeResult = Result.failure(TwinteApiException(401, "unauthorized")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        assertFailsWith<TwinteApiException> {
            repository.getMe()
        }
        assertNull(store.getSessionId())
    }

    @Test
    fun signOutLogsOutRemotelyAndClearsSession() = runTest {
        val store = MemorySessionStore("session-id")
        val authApi = FakeAuthApi()
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        val result = repository.signOut()

        assertEquals(TwinteSession("session-id"), authApi.loggedOutSession)
        assertNull(store.getSessionId())
        assertEquals(SignOutResult.RemoteAndLocal, result)
    }

    @Test
    fun signOutClearsSessionEvenWhenRemoteLogoutFails() = runTest {
        val store = MemorySessionStore("session-id")
        val authApi = FakeAuthApi(
            logoutResult = Result.failure(TwinteApiException(500, "failed")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        val result = repository.signOut()

        assertEquals(TwinteSession("session-id"), authApi.loggedOutSession)
        assertNull(store.getSessionId())
        assertIs<SignOutResult.LocalOnly>(result)
    }

    @Test
    fun deleteUserAuthenticationRefreshesUser() = runTest {
        val store = MemorySessionStore("session-id")
        val authApi = FakeAuthApi()
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        val session = repository.deleteUserAuthentication(AuthProvider.Apple)

        assertEquals("session-id", session.sessionId)
        assertEquals(AuthProvider.Apple, authApi.deletedProvider)
        assertEquals(TwinteSession("session-id"), authApi.lastSession)
    }

    @Test
    fun deleteUserAuthenticationClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val authApi = FakeAuthApi(
            deleteUserAuthenticationResult = Result.failure(
                TwinteApiException(401, "unauthorized"),
            ),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        assertFailsWith<TwinteApiException> {
            repository.deleteUserAuthentication(AuthProvider.Apple)
        }
        assertNull(store.getSessionId())
    }

    @Test
    fun deleteAccountDeletesRemoteAccountAndClearsSession() = runTest {
        val store = MemorySessionStore("session-id")
        val authApi = FakeAuthApi()
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        repository.deleteAccount()

        assertEquals(TwinteSession("session-id"), authApi.deletedAccountSession)
        assertNull(store.getSessionId())
    }

    @Test
    fun deleteAccountKeepsStoredSessionWhenRequestFailsWithoutUnauthorized() = runTest {
        val store = MemorySessionStore("session-id")
        val authApi = FakeAuthApi(
            deleteAccountResult = Result.failure(TwinteApiException(500, "failed")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
        )

        assertFailsWith<TwinteApiException> {
            repository.deleteAccount()
        }
        assertEquals("session-id", store.getSessionId())
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
        private val logoutResult: Result<Unit> = Result.success(Unit),
        private val deleteUserAuthenticationResult: Result<Unit> = Result.success(Unit),
        private val deleteAccountResult: Result<Unit> = Result.success(Unit),
    ) : AuthApi {
        var lastSession: TwinteSession? = null
            private set
        var loggedOutSession: TwinteSession? = null
            private set
        var deletedProvider: AuthProvider? = null
            private set
        var deletedAccountSession: TwinteSession? = null
            private set

        override suspend fun getMe(session: TwinteSession): User {
            lastSession = session
            return getMeResult.getOrThrow()
        }

        override suspend fun logout(session: TwinteSession) {
            loggedOutSession = session
            logoutResult.getOrThrow()
        }

        override suspend fun deleteUserAuthentication(session: TwinteSession, provider: AuthProvider) {
            lastSession = session
            deletedProvider = provider
            deleteUserAuthenticationResult.getOrThrow()
        }

        override suspend fun deleteAccount(session: TwinteSession) {
            deletedAccountSession = session
            deleteAccountResult.getOrThrow()
        }
    }

    private class FakeGoogleSessionApi(
        private val session: TwinteSession,
    ) : GoogleSessionApi {
        var requestedIdToken: String? = null
            private set

        var requestedCurrentSession: TwinteSession? = null
            private set

        override suspend fun createSessionWithIdToken(
            idToken: String,
            currentSession: TwinteSession?,
        ): TwinteSession {
            requestedIdToken = idToken
            requestedCurrentSession = currentSession
            return session
        }
    }

    private class FakeAppleSessionApi(
        private val session: TwinteSession,
    ) : AppleSessionApi {
        var requestedCredential: AppleSignInCredential? = null
            private set

        var requestedCurrentSession: TwinteSession? = null
            private set

        override suspend fun createSessionWithCredential(
            credential: AppleSignInCredential,
            currentSession: TwinteSession?,
        ): TwinteSession {
            requestedCredential = credential
            requestedCurrentSession = currentSession
            return session
        }
    }
}

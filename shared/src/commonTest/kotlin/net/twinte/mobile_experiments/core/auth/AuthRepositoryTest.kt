package net.twinte.mobile_experiments.core.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.api.TwinteNetworkException
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    @Test
    fun restoreCachedSessionDoesNotWaitForNetwork() = runTest {
        val cachedUser = User("cached-user-id", emptyList())
        val authApi = FakeAuthApi(getMeResult = Result.failure(IllegalStateException("network called")))
        val repository = AuthRepository(
            sessionStore = MemorySessionStore("session-id"),
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
            accountLocalDataSource = MemoryAccountLocalDataSource(cachedUser),
        )

        assertEquals(AuthSession("session-id", cachedUser), repository.restoreCachedSession())
        assertNull(authApi.lastSession)
    }

    @Test
    fun restoreSessionClearsCachedAccountWhenSessionIsMissing() = runTest {
        val accountStore = MemoryAccountLocalDataSource(User("cached-user-id", emptyList()))
        val repository = AuthRepository(
            sessionStore = MemorySessionStore(),
            authApi = FakeAuthApi(),
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
            accountLocalDataSource = accountStore,
        )

        assertNull(repository.restoreSession())
        assertNull(accountStore.getActiveUser())
    }

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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
        )

        assertFailsWith<TwinteApiException> {
            repository.signInWithGoogleIdToken("id-token")
        }

        assertEquals("previous-session-id", store.getSessionId())
    }

    @Test
    fun restoreSessionReturnsNullAndClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val accountStore = MemoryAccountLocalDataSource(User("user-id", emptyList()))
        val authApi = FakeAuthApi(
            getMeResult = Result.failure(TwinteApiException(401, "unauthorized")),
        )
        val repository = AuthRepository(
            sessionStore = store,
            authApi = authApi,
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
            accountLocalDataSource = accountStore,
        )

        assertNull(repository.restoreSession())
        assertNull(store.getSessionId())
        assertNull(accountStore.getActiveUser())
    }

    @Test
    fun restoreSessionUsesCachedUserWhenNetworkIsUnavailable() = runTest {
        val cachedUser = User("cached-user-id", emptyList())
        val store = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(cachedUser)
        val repository = AuthRepository(
            sessionStore = store,
            authApi = FakeAuthApi(
                getMeResult = Result.failure(
                    TwinteNetworkException(IllegalStateException("offline")),
                ),
            ),
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
            accountLocalDataSource = accountStore,
        )

        val restored = repository.restoreSession()

        assertEquals("session-id", restored?.sessionId)
        assertEquals(cachedUser, restored?.user)
        assertEquals(true, restored?.isOffline)
    }

    @Test
    fun cancelledValidationCannotRewriteCachedAccount() = runTest {
        val pendingResponse = CompletableDeferred<Unit>()
        val cachedUser = User("cached-user-id", emptyList())
        val sessionStore = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(cachedUser)
        val repository = AuthRepository(
            sessionStore = sessionStore,
            authApi = FakeAuthApi(getMeOperation = {
                withContext(NonCancellable) { pendingResponse.await() }
            }),
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
            accountLocalDataSource = accountStore,
        )
        val validation = launch { repository.restoreSession() }
        runCurrent()

        validation.cancel()
        pendingResponse.complete(Unit)
        validation.join()

        assertEquals(cachedUser, accountStore.getActiveUser())
        assertEquals("session-id", sessionStore.getSessionId())
    }

    @Test
    fun cancelledUnauthorizedValidationCannotClearCachedAccount() = runTest {
        val pendingResponse = CompletableDeferred<Unit>()
        val cachedUser = User("cached-user-id", emptyList())
        val sessionStore = MemorySessionStore("session-id")
        val accountStore = MemoryAccountLocalDataSource(cachedUser)
        val repository = AuthRepository(
            sessionStore = sessionStore,
            authApi = FakeAuthApi(
                getMeResult = Result.failure(TwinteApiException(401, "unauthorized")),
                getMeOperation = { withContext(NonCancellable) { pendingResponse.await() } },
            ),
            googleSessionApi = FakeGoogleSessionApi(TwinteSession("unused")),
            appleSessionApi = FakeAppleSessionApi(TwinteSession("unused")),
            accountLocalDataSource = accountStore,
        )
        val validation = launch { repository.restoreSession() }
        runCurrent()

        validation.cancel()
        pendingResponse.complete(Unit)
        validation.join()

        assertEquals(cachedUser, accountStore.getActiveUser())
        assertEquals("session-id", sessionStore.getSessionId())
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
            accountLocalDataSource = MemoryAccountLocalDataSource(),
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
        private val getMeOperation: suspend () -> Unit = {},
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
            getMeOperation()
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

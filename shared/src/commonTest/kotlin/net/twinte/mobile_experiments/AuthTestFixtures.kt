package net.twinte.mobile_experiments

import net.twinte.mobile_experiments.core.auth.AppleSignInCredential
import net.twinte.mobile_experiments.core.auth.AuthService
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.SignOutResult
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

internal class FakeAuthService(
    private val restoreResult: AuthSession? = null,
    private val cachedResult: AuthSession? = null,
    private val restoreOperation: suspend () -> AuthSession? = { restoreResult },
    private val signOutResult: SignOutResult = SignOutResult.RemoteAndLocal,
) : AuthService {
    override suspend fun restoreCachedSession(): AuthSession? = cachedResult

    override suspend fun restoreSession(): AuthSession? = restoreOperation()

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthSession =
        error("Not used")

    override suspend fun signInWithAppleCredential(credential: AppleSignInCredential): AuthSession =
        error("Not used")

    override suspend fun getMe(): User = error("Not used")

    override suspend fun signOut(): SignOutResult = signOutResult

    override suspend fun deleteUserAuthentication(provider: AuthProvider): AuthSession =
        error("Not used")

    override suspend fun deleteAccount() = error("Not used")
}

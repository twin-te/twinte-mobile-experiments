package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

interface AuthService {
    suspend fun restoreSession(): AuthSession?

    suspend fun signInWithGoogleIdToken(idToken: String): AuthSession

    suspend fun signInWithAppleCredential(credential: AppleSignInCredential): AuthSession

    suspend fun getMe(): User

    suspend fun signOut(): SignOutResult

    suspend fun deleteUserAuthentication(provider: AuthProvider): AuthSession

    suspend fun deleteAccount()
}

package net.twinte.mobile_experiments

import net.twinte.mobile_experiments.core.auth.AppleSignInCredential

interface GoogleIdTokenProvider {
    val isConfigured: Boolean

    suspend fun requestIdToken(): GoogleIdTokenResult
}

data class GoogleIdTokenResult(
    val idToken: String? = null,
    val isCanceled: Boolean = false,
)

object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
    override val isConfigured: Boolean = false

    override suspend fun requestIdToken(): GoogleIdTokenResult = GoogleIdTokenResult()
}

interface AppleSignInCredentialProvider {
    val isConfigured: Boolean

    suspend fun requestCredential(): AppleSignInCredentialResult
}

data class AppleSignInCredentialResult(
    val credential: AppleSignInCredential? = null,
    val isCanceled: Boolean = false,
)

object UnavailableAppleSignInCredentialProvider : AppleSignInCredentialProvider {
    override val isConfigured: Boolean = false

    override suspend fun requestCredential(): AppleSignInCredentialResult = AppleSignInCredentialResult()
}

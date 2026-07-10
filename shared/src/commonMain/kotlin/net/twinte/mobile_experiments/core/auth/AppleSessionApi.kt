package net.twinte.mobile_experiments.core.auth

interface AppleSessionApi {
    suspend fun createSessionWithCredential(
        credential: AppleSignInCredential,
        currentSession: TwinteSession? = null,
    ): TwinteSession
}

data class AppleSignInCredential(
    val idToken: String,
    val authorizationCode: String?,
)

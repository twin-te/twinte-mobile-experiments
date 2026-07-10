package net.twinte.mobile_experiments.core.auth

interface AppleSessionApi {
    suspend fun createChallenge(): AuthChallenge

    suspend fun createSessionWithCredential(
        credential: AppleSignInCredential,
        challengeId: String,
        currentSession: TwinteSession? = null,
    ): TwinteSession
}

data class AppleSignInCredential(
    val idToken: String,
    val authorizationCode: String?,
)

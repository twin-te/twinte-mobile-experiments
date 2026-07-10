package net.twinte.mobile_experiments.core.auth

interface GoogleSessionApi {
    suspend fun createChallenge(): AuthChallenge

    suspend fun createSessionWithIdToken(
        idToken: String,
        challengeId: String,
        currentSession: TwinteSession? = null,
    ): TwinteSession
}

data class AuthChallenge(
    val id: String,
    val nonce: String,
)

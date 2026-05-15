package net.twinte.mobile_experiments.core.auth

interface GoogleSessionApi {
    suspend fun createSessionWithIdToken(idToken: String): TwinteSession
}

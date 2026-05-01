package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

interface AuthRepository {
    suspend fun getMe(): Result<User>

    suspend fun signIn(provider: AuthProvider): Result<Unit>

    suspend fun signOut(): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>
}

interface PlatformAuthGateway {
    suspend fun signIn(provider: AuthProvider): Result<Unit>

    suspend fun signOut(): Result<Unit>
}

interface SessionStore {
    suspend fun hasSession(): Boolean

    suspend fun clearSession()
}

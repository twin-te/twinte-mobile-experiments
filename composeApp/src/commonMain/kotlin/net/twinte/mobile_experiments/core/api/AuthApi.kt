package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

interface AuthApi {
    suspend fun getMe(): User

    suspend fun deleteAccount()

    suspend fun deleteUserAuthentication(provider: AuthProvider)
}

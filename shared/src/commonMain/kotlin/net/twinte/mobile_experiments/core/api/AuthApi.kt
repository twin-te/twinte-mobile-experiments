package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User

interface AuthApi {
    suspend fun getMe(session: TwinteSession): User

    suspend fun logout(session: TwinteSession)

    suspend fun deleteUserAuthentication(session: TwinteSession, provider: AuthProvider)

    suspend fun deleteAccount(session: TwinteSession)
}

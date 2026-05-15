package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.User

interface AuthApi {
    suspend fun getMe(session: TwinteSession): User
}

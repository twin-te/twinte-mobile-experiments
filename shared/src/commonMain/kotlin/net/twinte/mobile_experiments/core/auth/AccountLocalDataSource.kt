package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.User

interface AccountLocalDataSource {
    suspend fun getActiveUser(): User?

    suspend fun saveActiveUser(user: User)

    suspend fun clearActiveAccountAndData()
}

class MemoryAccountLocalDataSource(
    initialUser: User? = null,
) : AccountLocalDataSource {
    private var user = initialUser

    override suspend fun getActiveUser(): User? = user

    override suspend fun saveActiveUser(user: User) {
        this.user = user
    }

    override suspend fun clearActiveAccountAndData() {
        user = null
    }
}

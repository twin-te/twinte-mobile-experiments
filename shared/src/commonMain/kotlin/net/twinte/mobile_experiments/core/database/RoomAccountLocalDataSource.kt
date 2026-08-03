package net.twinte.mobile_experiments.core.database

import net.twinte.mobile_experiments.core.auth.AccountLocalDataSource
import net.twinte.mobile_experiments.core.domain.User

class RoomAccountLocalDataSource(
    private val accountDao: AccountDao,
) : AccountLocalDataSource {
    override suspend fun getActiveUser(): User? =
        accountDao.getActiveUser()

    override suspend fun saveActiveUser(user: User) {
        accountDao.saveActiveUser(user)
    }

    override suspend fun clearActiveAccountAndData() {
        accountDao.clearActiveAccountAndData()
    }
}

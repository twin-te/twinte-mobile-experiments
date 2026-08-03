package net.twinte.mobile_experiments.core.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication

@Dao
abstract class AccountDao {
    @Query("SELECT accountId FROM active_account WHERE slot = 0")
    abstract suspend fun getActiveAccountId(): String?

    @Query(
        """
        SELECT user_authentication.*
        FROM user_authentication
        INNER JOIN active_account ON active_account.accountId = user_authentication.accountId
        WHERE active_account.slot = 0
        ORDER BY user_authentication.provider
        """,
    )
    abstract suspend fun getActiveUserAuthentications(): List<UserAuthenticationEntity>

    @Upsert
    protected abstract suspend fun insertAccount(account: AccountEntity)

    @Upsert
    protected abstract suspend fun setActiveAccount(activeAccount: ActiveAccountEntity)

    @Upsert
    protected abstract suspend fun insertAuthentications(authentications: List<UserAuthenticationEntity>)

    @Query("DELETE FROM user_authentication WHERE accountId = :accountId")
    protected abstract suspend fun deleteAuthentications(accountId: String)

    @Query("DELETE FROM active_account WHERE slot = 0")
    protected abstract suspend fun deleteActiveAccount()

    @Query("DELETE FROM account WHERE id = :accountId")
    protected abstract suspend fun deleteAccount(accountId: String)

    @Transaction
    open suspend fun saveActiveUser(user: User) {
        val previousAccountId = getActiveAccountId()
        if (previousAccountId != null && previousAccountId != user.id) {
            deleteActiveAccount()
            deleteAccount(previousAccountId)
        }
        insertAccount(AccountEntity(user.id))
        deleteAuthentications(user.id)
        if (user.authentications.isNotEmpty()) {
            insertAuthentications(
                user.authentications.map {
                    UserAuthenticationEntity(
                        accountId = user.id,
                        provider = it.provider.name,
                        socialId = it.socialId,
                    )
                },
            )
        }
        setActiveAccount(ActiveAccountEntity(accountId = user.id))
    }

    @Transaction
    open suspend fun clearActiveAccountAndData() {
        val accountId = getActiveAccountId()
        deleteActiveAccount()
        if (accountId != null) {
            deleteAccount(accountId)
        }
    }

    suspend fun getActiveUser(): User? {
        val accountId = getActiveAccountId() ?: return null
        val authentications = getActiveUserAuthentications().mapNotNull { entity ->
            AuthProvider.entries.firstOrNull { it.name == entity.provider }?.let { provider ->
                UserAuthentication(provider = provider, socialId = entity.socialId)
            }
        }
        return User(id = accountId, authentications = authentications)
    }
}

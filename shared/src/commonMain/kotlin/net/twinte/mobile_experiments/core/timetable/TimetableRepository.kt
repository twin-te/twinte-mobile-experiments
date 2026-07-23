package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.AccountLocalDataSource
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.auth.getSession
import kotlin.time.Clock

class TimetableRepository(
    private val sessionStore: SessionStore,
    private val accountLocalDataSource: AccountLocalDataSource,
    private val timetableApi: TimetableApi,
    private val localDataSource: TimetableLocalDataSource,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : TimetableService {
    override fun observeTimetable(accountId: String, year: Int?): Flow<TimetableSnapshot> =
        localDataSource.observeSnapshot(accountId, year)

    override suspend fun refresh(accountId: String, year: Int?) {
        require(accountLocalDataSource.getActiveUser()?.id == accountId) {
            "The requested account is not active"
        }
        withAuthenticatedSession { session ->
            val courses = timetableApi.listRegisteredCourses(session, year)
            val tags = timetableApi.listTags(session)
            localDataSource.replaceSnapshot(
                accountId = accountId,
                year = year,
                courses = courses,
                tags = tags,
                syncedAtMillis = nowMillis(),
            )
        }
    }

    private suspend fun requireSession(): TwinteSession =
        requireNotNull(sessionStore.getSession()) { "No session" }

    private suspend fun <T> withAuthenticatedSession(block: suspend (TwinteSession) -> T): T {
        val session = requireSession()
        return runCatching {
            block(session)
        }.onFailure { error ->
            if (error is TwinteApiException && error.isUnauthorized) {
                sessionStore.clearSessionId()
                accountLocalDataSource.clearActiveAccountAndData()
            }
        }.getOrThrow()
    }
}

package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyTimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.AccountLocalDataSource
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.auth.getSession
import kotlin.time.Clock

class DailyTimetableRepository(
    private val sessionStore: SessionStore,
    private val accountLocalDataSource: AccountLocalDataSource,
    private val api: DailyTimetableApi,
    private val localDataSource: DailyTimetableLocalDataSource,
    private val builder: DailyTimetableBuilder = DailyTimetableBuilder(),
    private val now: () -> kotlin.time.Instant = Clock.System::now,
) : DailyTimetableService {
    override fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?> =
        localDataSource.observe(accountId, date).map { cached ->
            cached?.let {
                DailyTimetableSnapshot(
                    timetable = builder.build(
                        DailyTimetableInput(
                            date = it.snapshot.date,
                            module = it.snapshot.module,
                            events = it.snapshot.events,
                            courses = it.snapshot.courses,
                        ),
                    ),
                    lastSuccessfulSyncAt = it.lastSuccessfulSyncAt,
                )
            }
        }

    override suspend fun refresh(accountId: String, date: LocalDate) {
        require(accountLocalDataSource.getActiveUser()?.id == accountId) {
            "The requested account is not active"
        }
        val session = requireNotNull(sessionStore.getSession()) { "No session" }
        try {
            localDataSource.replace(
                accountId = accountId,
                snapshot = api.getByDate(session, date),
                syncedAt = now(),
            )
        } catch (error: Throwable) {
            if (error is TwinteApiException && error.isUnauthorized) {
                sessionStore.clearSessionId()
                accountLocalDataSource.clearActiveAccountAndData()
            }
            throw error
        }
    }
}

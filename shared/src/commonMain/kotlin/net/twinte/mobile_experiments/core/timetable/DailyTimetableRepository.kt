package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyTimetableApi
import net.twinte.mobile_experiments.core.auth.AuthenticatedSessionProvider
import kotlin.time.Clock

class DailyTimetableRepository(
    private val authenticatedSessionProvider: AuthenticatedSessionProvider,
    private val api: DailyTimetableApi,
    private val localDataSource: DailyTimetableLocalDataSource,
    private val builder: DailyTimetableBuilder = DailyTimetableBuilder(),
    private val now: () -> kotlin.time.Instant = Clock.System::now,
) : DailyTimetableService {
    private val refreshMutex = Mutex()

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
        refreshMutex.withLock {
            authenticatedSessionProvider.withActiveSession(accountId) { session ->
                val snapshot = api.getByDate(session, date)
                currentCoroutineContext().ensureActive()
                localDataSource.replace(
                    accountId = accountId,
                    snapshot = snapshot,
                    syncedAt = now(),
                )
            }
        }
    }
}

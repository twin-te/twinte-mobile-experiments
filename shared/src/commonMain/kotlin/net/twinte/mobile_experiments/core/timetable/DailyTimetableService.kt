package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class DailyTimetableSnapshot(
    val timetable: DailyTimetable,
    val lastSuccessfulSyncAt: Instant,
)

interface DailyTimetableService {
    fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?>

    suspend fun refresh(accountId: String, date: LocalDate)
}

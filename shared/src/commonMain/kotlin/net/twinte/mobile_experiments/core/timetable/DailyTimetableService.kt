package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

data class DailyTimetableSnapshot(
    val timetable: DailyTimetable,
    val lastSuccessfulSyncAtMillis: Long,
)

interface DailyTimetableService {
    fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?>

    suspend fun refresh(accountId: String, date: LocalDate)
}

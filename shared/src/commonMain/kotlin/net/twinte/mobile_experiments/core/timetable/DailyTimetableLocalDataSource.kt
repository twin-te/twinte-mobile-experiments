package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot

data class CachedDailyTimetable(
    val snapshot: DailyApiSnapshot,
    val lastSuccessfulSyncAtMillis: Long,
)

interface DailyTimetableLocalDataSource {
    fun observe(accountId: String, date: LocalDate): Flow<CachedDailyTimetable?>

    suspend fun replace(
        accountId: String,
        snapshot: DailyApiSnapshot,
        syncedAtMillis: Long,
    )
}

package net.twinte.mobile_experiments.core.api

import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.DailyCourse
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent

data class DailyApiSnapshot(
    val date: LocalDate,
    val module: Module,
    val events: List<SchoolCalendarEvent>,
    val courses: List<DailyCourse>,
)

interface DailyTimetableApi {
    suspend fun getByDate(session: TwinteSession, date: LocalDate): DailyApiSnapshot
}

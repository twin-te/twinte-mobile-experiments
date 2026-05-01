package net.twinte.mobile_experiments.core.api

import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.domain.Tag

data class DailyApiSnapshot(
    val date: LocalDate,
    val module: net.twinte.mobile_experiments.core.domain.Module,
    val events: List<SchoolCalendarEvent>,
    val registeredCourses: List<RegisteredCourse>,
)

interface TimetableApi {
    suspend fun getByDate(date: LocalDate): DailyApiSnapshot

    suspend fun listRegisteredCourses(year: Int? = null): List<RegisteredCourse>

    suspend fun listTags(): List<Tag>
}

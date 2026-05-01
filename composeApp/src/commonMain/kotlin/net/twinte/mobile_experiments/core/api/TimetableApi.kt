package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.domain.Tag

data class DailyApiSnapshot(
    val date: String,
    val module: net.twinte.mobile_experiments.core.domain.Module,
    val events: List<SchoolCalendarEvent>,
    val registeredCourses: List<RegisteredCourse>,
)

interface TimetableApi {
    suspend fun getByDate(date: String): DailyApiSnapshot

    suspend fun listRegisteredCourses(year: Int? = null): List<RegisteredCourse>

    suspend fun listTags(): List<Tag>
}

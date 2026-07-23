package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.domain.Tag

data class DailyApiSnapshot(
    val date: String,
    val module: Module,
    val events: List<SchoolCalendarEvent>,
    val registeredCourses: List<RegisteredCourse>,
)

interface TimetableApi {
    suspend fun listRegisteredCourses(session: TwinteSession, year: Int? = null): List<RegisteredCourse>

    suspend fun listTags(session: TwinteSession): List<Tag>
}

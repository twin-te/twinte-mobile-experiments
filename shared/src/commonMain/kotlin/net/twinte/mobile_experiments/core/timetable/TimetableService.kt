package net.twinte.mobile_experiments.core.timetable

import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag

interface TimetableService {
    suspend fun listRegisteredCourses(year: Int? = null): List<RegisteredCourse>

    suspend fun listTags(): List<Tag>
}

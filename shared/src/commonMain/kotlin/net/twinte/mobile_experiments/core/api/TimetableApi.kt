package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag

interface TimetableApi {
    suspend fun listRegisteredCourses(session: TwinteSession, year: Int? = null): List<RegisteredCourse>

    suspend fun listTags(session: TwinteSession): List<Tag>
}

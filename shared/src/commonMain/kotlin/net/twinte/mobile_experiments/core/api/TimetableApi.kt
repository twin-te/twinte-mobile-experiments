package net.twinte.mobile_experiments.core.api

import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

interface TimetableApi {
    suspend fun searchCourses(input: SearchCoursesInput): List<Course>

    suspend fun listRegisteredCourses(session: TwinteSession, year: Int? = null): List<RegisteredCourse>

    suspend fun listTags(session: TwinteSession): List<Tag>

    suspend fun createRegisteredCoursesByCodes(
        session: TwinteSession,
        year: Int,
        codes: List<String>,
    ): List<RegisteredCourse>

    suspend fun createRegisteredCourseManually(
        session: TwinteSession,
        input: CreateRegisteredCourseInput,
    ): RegisteredCourse

    suspend fun updateRegisteredCourse(
        session: TwinteSession,
        input: UpdateRegisteredCourseInput,
    ): RegisteredCourse

    suspend fun deleteRegisteredCourse(session: TwinteSession, id: String)

    suspend fun createTag(session: TwinteSession, name: String): Tag

    suspend fun updateTag(session: TwinteSession, id: String, name: String): Tag

    suspend fun deleteTag(session: TwinteSession, id: String)

    suspend fun rearrangeTags(session: TwinteSession, ids: List<String>): List<Tag>
}

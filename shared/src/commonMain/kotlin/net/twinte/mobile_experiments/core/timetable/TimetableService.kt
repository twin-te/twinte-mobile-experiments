package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import kotlin.time.Instant

data class TimetableSnapshot(
    val courses: List<RegisteredCourse> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val lastSuccessfulSyncAt: Instant? = null,
)

interface TimetableService {
    suspend fun searchCourses(input: SearchCoursesInput): List<Course>

    fun observeTimetable(accountId: String, year: Int? = null): Flow<TimetableSnapshot>

    suspend fun refresh(accountId: String, year: Int? = null)

    suspend fun createCoursesByCodes(accountId: String, year: Int, codes: List<String>): List<RegisteredCourse>

    suspend fun createCourseManually(accountId: String, input: CreateRegisteredCourseInput): RegisteredCourse

    suspend fun updateCourse(accountId: String, input: UpdateRegisteredCourseInput): RegisteredCourse

    suspend fun deleteCourse(accountId: String, id: String)

    suspend fun createTag(accountId: String, name: String): Tag

    suspend fun updateTag(accountId: String, id: String, name: String): Tag

    suspend fun deleteTag(accountId: String, id: String)

    suspend fun rearrangeTags(accountId: String, ids: List<String>): List<Tag>
}

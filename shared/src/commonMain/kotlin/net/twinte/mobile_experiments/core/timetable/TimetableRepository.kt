package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.auth.AuthenticatedSessionProvider
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import kotlin.time.Clock

class TimetableRepository(
    private val authenticatedSessionProvider: AuthenticatedSessionProvider,
    private val timetableApi: TimetableApi,
    private val localDataSource: TimetableLocalDataSource,
    private val now: () -> kotlin.time.Instant = Clock.System::now,
) : TimetableService {
    override suspend fun searchCourses(input: SearchCoursesInput): List<Course> =
        timetableApi.searchCourses(input)

    override fun observeTimetable(accountId: String, year: Int?): Flow<TimetableSnapshot> =
        localDataSource.observeSnapshot(accountId, year)

    override suspend fun refresh(accountId: String, year: Int?) {
        authenticatedSessionProvider.withActiveSession(accountId) { session ->
            val courses = timetableApi.listRegisteredCourses(session, year)
            val tags = timetableApi.listTags(session)
            localDataSource.replaceSnapshot(
                accountId = accountId,
                year = year,
                courses = courses,
                tags = tags,
                syncedAt = now(),
            )
        }
    }

    override suspend fun createCoursesByCodes(
        accountId: String,
        year: Int,
        codes: List<String>,
    ): List<RegisteredCourse> = authenticatedSessionProvider.withActiveSession(accountId) { session ->
        timetableApi.createRegisteredCoursesByCodes(session, year, codes).also { courses ->
            localDataSource.upsertCourses(accountId, courses)
        }
    }

    override suspend fun createCourseManually(
        accountId: String,
        input: CreateRegisteredCourseInput,
    ): RegisteredCourse = authenticatedSessionProvider.withActiveSession(accountId) { session ->
        timetableApi.createRegisteredCourseManually(session, input).also { course ->
            localDataSource.upsertCourses(accountId, listOf(course))
        }
    }

    override suspend fun updateCourse(
        accountId: String,
        input: UpdateRegisteredCourseInput,
    ): RegisteredCourse = authenticatedSessionProvider.withActiveSession(accountId) { session ->
        timetableApi.updateRegisteredCourse(session, input).also { course ->
            localDataSource.upsertCourses(accountId, listOf(course))
        }
    }

    override suspend fun deleteCourse(accountId: String, id: String) {
        authenticatedSessionProvider.withActiveSession(accountId) { session ->
            timetableApi.deleteRegisteredCourse(session, id)
            localDataSource.deleteCourse(accountId, id)
        }
    }

    override suspend fun createTag(accountId: String, name: String): Tag =
        authenticatedSessionProvider.withActiveSession(accountId) { session ->
            timetableApi.createTag(session, name).also { tag ->
                localDataSource.upsertTags(accountId, listOf(tag))
            }
        }

    override suspend fun updateTag(accountId: String, id: String, name: String): Tag =
        authenticatedSessionProvider.withActiveSession(accountId) { session ->
            timetableApi.updateTag(session, id, name).also { tag ->
                localDataSource.upsertTags(accountId, listOf(tag))
            }
        }

    override suspend fun deleteTag(accountId: String, id: String) {
        authenticatedSessionProvider.withActiveSession(accountId) { session ->
            timetableApi.deleteTag(session, id)
            localDataSource.deleteTag(accountId, id)
        }
    }

    override suspend fun rearrangeTags(accountId: String, ids: List<String>): List<Tag> =
        authenticatedSessionProvider.withActiveSession(accountId) { session ->
            timetableApi.rearrangeTags(session, ids).also { tags ->
                localDataSource.upsertTags(accountId, tags)
            }
        }

}

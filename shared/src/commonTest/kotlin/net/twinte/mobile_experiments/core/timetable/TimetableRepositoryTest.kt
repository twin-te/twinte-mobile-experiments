package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.MemoryAccountLocalDataSource
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TimetableRepositoryTest {
    @Test
    fun refreshStoresOneCompleteSnapshot() = runTest {
        val courses = listOf(course("course-id"))
        val tags = listOf(Tag("tag-id", "Required", 0))
        val localDataSource = FakeTimetableLocalDataSource()
        val repository = repository(
            timetableApi = FakeTimetableApi(
                listRegisteredCoursesResult = Result.success(courses),
                listTagsResult = Result.success(tags),
            ),
            localDataSource = localDataSource,
        )

        repository.refresh(accountId = "user-id", year = 2026)

        assertEquals(
            TimetableSnapshot(courses, tags, lastSuccessfulSyncAtMillis = 1234L),
            repository.observeTimetable("user-id", 2026).first(),
        )
        assertEquals(1, localDataSource.replaceCount)
    }

    @Test
    fun refreshDoesNotReplaceCacheWhenOneRequestFails() = runTest {
        val cached = TimetableSnapshot(
            courses = listOf(course("cached-course")),
            lastSuccessfulSyncAtMillis = 1000L,
        )
        val localDataSource = FakeTimetableLocalDataSource(cached)
        val repository = repository(
            timetableApi = FakeTimetableApi(
                listRegisteredCoursesResult = Result.success(listOf(course("new-course"))),
                listTagsResult = Result.failure(TwinteApiException(500, "failed")),
            ),
            localDataSource = localDataSource,
        )

        assertFailsWith<TwinteApiException> {
            repository.refresh("user-id")
        }

        assertEquals(cached, repository.observeTimetable("user-id").first())
        assertEquals(0, localDataSource.replaceCount)
    }

    @Test
    fun refreshClearsSessionAndAccountDataOnUnauthorized() = runTest {
        val sessionStore = MemorySessionStore("stale-session-id")
        val accountStore = MemoryAccountLocalDataSource(User("user-id", emptyList()))
        val localDataSource = FakeTimetableLocalDataSource(
            TimetableSnapshot(courses = listOf(course("cached-course"))),
        )
        val repository = TimetableRepository(
            sessionStore = sessionStore,
            accountLocalDataSource = accountStore,
            timetableApi = FakeTimetableApi(
                listRegisteredCoursesResult = Result.failure(TwinteApiException(401, "unauthorized")),
            ),
            localDataSource = localDataSource,
        )

        assertFailsWith<TwinteApiException> {
            repository.refresh("user-id")
        }

        assertNull(sessionStore.getSessionId())
        assertNull(accountStore.getActiveUser())
    }

    private fun repository(
        timetableApi: TimetableApi,
        localDataSource: TimetableLocalDataSource,
    ): TimetableRepository =
        TimetableRepository(
            sessionStore = MemorySessionStore("session-id"),
            accountLocalDataSource = MemoryAccountLocalDataSource(User("user-id", emptyList())),
            timetableApi = timetableApi,
            localDataSource = localDataSource,
            nowMillis = { 1234L },
        )

    private class FakeTimetableApi(
        private val listRegisteredCoursesResult: Result<List<RegisteredCourse>> = Result.success(emptyList()),
        private val listTagsResult: Result<List<Tag>> = Result.success(emptyList()),
    ) : TimetableApi {
        override suspend fun listRegisteredCourses(session: TwinteSession, year: Int?): List<RegisteredCourse> =
            listRegisteredCoursesResult.getOrThrow()

        override suspend fun listTags(session: TwinteSession): List<Tag> =
            listTagsResult.getOrThrow()
    }

    private class FakeTimetableLocalDataSource(
        initialSnapshot: TimetableSnapshot = TimetableSnapshot(),
    ) : TimetableLocalDataSource {
        private val snapshots = MutableStateFlow(initialSnapshot)
        var replaceCount = 0
            private set

        override fun observeSnapshot(accountId: String, year: Int?): Flow<TimetableSnapshot> =
            snapshots

        override suspend fun replaceSnapshot(
            accountId: String,
            year: Int?,
            courses: List<RegisteredCourse>,
            tags: List<Tag>,
            syncedAtMillis: Long,
        ) {
            replaceCount += 1
            snapshots.value = TimetableSnapshot(courses, tags, syncedAtMillis)
        }
    }
}

private fun course(id: String): RegisteredCourse =
    RegisteredCourse(
        id = id,
        year = 2026,
        name = "Course $id",
        instructors = "Teacher",
        credit = "1.0",
        methods = emptyList(),
        schedules = emptyList(),
    )

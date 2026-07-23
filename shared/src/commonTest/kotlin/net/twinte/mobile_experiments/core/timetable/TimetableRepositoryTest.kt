package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TimetableRepositoryTest {
    @Test
    fun listRegisteredCoursesClearsStoredSessionOnUnauthorized() = runTest {
        val store = MemorySessionStore("stale-session-id")
        val repository = TimetableRepository(
            sessionStore = store,
            timetableApi = FakeTimetableApi(
                listRegisteredCoursesResult = Result.failure(TwinteApiException(401, "unauthorized")),
            ),
        )

        assertFailsWith<TwinteApiException> {
            repository.listRegisteredCourses()
        }
        assertNull(store.getSessionId())
    }

    private class FakeTimetableApi(
        private val listRegisteredCoursesResult: Result<List<RegisteredCourse>> = Result.success(emptyList()),
        private val listTagsResult: Result<List<Tag>> = Result.success(emptyList()),
    ) : TimetableApi {
        override suspend fun listRegisteredCourses(session: TwinteSession, year: Int?): List<RegisteredCourse> =
            listRegisteredCoursesResult.getOrThrow()

        override suspend fun listTags(session: TwinteSession): List<Tag> =
            listTagsResult.getOrThrow()
    }
}

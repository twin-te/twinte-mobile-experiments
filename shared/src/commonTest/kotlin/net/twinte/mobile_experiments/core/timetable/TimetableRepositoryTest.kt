package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.AuthenticatedSessionProvider
import net.twinte.mobile_experiments.core.auth.MemoryAccountLocalDataSource
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
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
            TimetableSnapshot(courses, tags, lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(1234)),
            repository.observeTimetable("user-id", 2026).first(),
        )
        assertEquals(1, localDataSource.replaceCount)
    }

    @Test
    fun refreshDoesNotReplaceCacheWhenOneRequestFails() = runTest {
        val cached = TimetableSnapshot(
            courses = listOf(course("cached-course")),
            lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(1000),
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
    fun cancelledRefreshDoesNotRestoreCacheAfterSignOut() = runTest {
        val pendingResponse = CompletableDeferred<Unit>()
        val localDataSource = FakeTimetableLocalDataSource()
        val repository = repository(
            timetableApi = FakeTimetableApi(
                listRegisteredCoursesResult = Result.success(listOf(course("stale-course"))),
                listCoursesOperation = { withContext(NonCancellable) { pendingResponse.await() } },
            ),
            localDataSource = localDataSource,
        )
        val refresh = launch { repository.refresh("user-id") }
        runCurrent()

        refresh.cancel()
        pendingResponse.complete(Unit)
        refresh.join()

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
            authenticatedSessionProvider = AuthenticatedSessionProvider(sessionStore, accountStore),
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

    @Test
    fun successfulMutationsUpdateCachedCoursesAndTags() = runTest {
        val originalCourse = course("course-id")
        val updatedCourse = originalCourse.copy(name = "Updated")
        val originalTag = Tag("tag-id", "Original", 0)
        val updatedTag = originalTag.copy(name = "Updated")
        val localDataSource = FakeTimetableLocalDataSource(
            TimetableSnapshot(
                courses = listOf(originalCourse),
                tags = listOf(originalTag),
                lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(1000),
            ),
        )
        val repository = repository(
            timetableApi = FakeTimetableApi(
                updateRegisteredCourseResult = Result.success(updatedCourse),
                updateTagResult = Result.success(updatedTag),
            ),
            localDataSource = localDataSource,
        )

        repository.updateCourse("user-id", UpdateRegisteredCourseInput("course-id", name = "Updated"))
        repository.updateTag("user-id", "tag-id", "Updated")

        assertEquals(
            TimetableSnapshot(
                courses = listOf(updatedCourse),
                tags = listOf(updatedTag),
                lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(1000),
            ),
            repository.observeTimetable("user-id").first(),
        )
    }

    @Test
    fun failedMutationDoesNotChangeCache() = runTest {
        val cached = TimetableSnapshot(courses = listOf(course("course-id")))
        val localDataSource = FakeTimetableLocalDataSource(cached)
        val repository = repository(
            timetableApi = FakeTimetableApi(
                updateRegisteredCourseResult = Result.failure(TwinteApiException(500, "failed")),
            ),
            localDataSource = localDataSource,
        )

        assertFailsWith<TwinteApiException> {
            repository.updateCourse("user-id", UpdateRegisteredCourseInput("course-id", name = "Updated"))
        }

        assertEquals(cached, repository.observeTimetable("user-id").first())
    }

    @Test
    fun successfulDeletesRemoveCachedCourseAndTagRelations() = runTest {
        val localDataSource = FakeTimetableLocalDataSource(
            TimetableSnapshot(
                courses = listOf(course("course-id").copy(tagIds = listOf("tag-id"))),
                tags = listOf(Tag("tag-id", "Required", 0)),
            ),
        )
        val repository = repository(
            timetableApi = FakeTimetableApi(
                deleteRegisteredCourseResult = Result.success(Unit),
                deleteTagResult = Result.success(Unit),
            ),
            localDataSource = localDataSource,
        )

        repository.deleteTag("user-id", "tag-id")
        assertEquals(emptyList(), repository.observeTimetable("user-id").first().courses.single().tagIds)

        repository.deleteCourse("user-id", "course-id")
        assertEquals(emptyList(), repository.observeTimetable("user-id").first().courses)
    }

    @Test
    fun fullRefreshCannotOverwriteConcurrentCourseUpdate() = runTest {
        val pendingRefresh = CompletableDeferred<Unit>()
        val original = course("course-id")
        val updated = original.copy(name = "Updated")
        var updateStarted = false
        val localDataSource = FakeTimetableLocalDataSource(TimetableSnapshot(courses = listOf(original)))
        val repository = repository(
            timetableApi = FakeTimetableApi(
                listRegisteredCoursesResult = Result.success(listOf(original)),
                updateRegisteredCourseResult = Result.success(updated),
                listCoursesOperation = { pendingRefresh.await() },
                updateCourseOperation = { updateStarted = true },
            ),
            localDataSource = localDataSource,
        )
        val refresh = launch { repository.refresh("user-id") }
        runCurrent()
        val update = launch {
            repository.updateCourse("user-id", UpdateRegisteredCourseInput(original.id, name = updated.name))
        }
        runCurrent()

        assertFalse(updateStarted)
        pendingRefresh.complete(Unit)
        refresh.join()
        update.join()

        assertEquals(listOf(updated), repository.observeTimetable("user-id").first().courses)
    }

    private fun repository(
        timetableApi: TimetableApi,
        localDataSource: TimetableLocalDataSource,
    ): TimetableRepository =
        TimetableRepository(
            authenticatedSessionProvider = AuthenticatedSessionProvider(
                sessionStore = MemorySessionStore("session-id"),
                accountLocalDataSource = MemoryAccountLocalDataSource(User("user-id", emptyList())),
            ),
            timetableApi = timetableApi,
            localDataSource = localDataSource,
            now = { Instant.fromEpochMilliseconds(1234) },
        )

    private class FakeTimetableApi(
        private val searchCoursesResult: Result<List<Course>> = Result.success(emptyList()),
        private val listRegisteredCoursesResult: Result<List<RegisteredCourse>> = Result.success(emptyList()),
        private val listTagsResult: Result<List<Tag>> = Result.success(emptyList()),
        private val createRegisteredCoursesByCodesResult: Result<List<RegisteredCourse>> = unusedResult(),
        private val createRegisteredCourseManuallyResult: Result<RegisteredCourse> = unusedResult(),
        private val updateRegisteredCourseResult: Result<RegisteredCourse> = unusedResult(),
        private val deleteRegisteredCourseResult: Result<Unit> = unusedResult(),
        private val createTagResult: Result<Tag> = unusedResult(),
        private val updateTagResult: Result<Tag> = unusedResult(),
        private val deleteTagResult: Result<Unit> = unusedResult(),
        private val rearrangeTagsResult: Result<List<Tag>> = unusedResult(),
        private val listCoursesOperation: suspend () -> Unit = {},
        private val updateCourseOperation: suspend () -> Unit = {},
    ) : TimetableApi {
        override suspend fun searchCourses(input: SearchCoursesInput): List<Course> =
            searchCoursesResult.getOrThrow()

        override suspend fun listRegisteredCourses(session: TwinteSession, year: Int?): List<RegisteredCourse> {
            listCoursesOperation()
            return listRegisteredCoursesResult.getOrThrow()
        }

        override suspend fun listTags(session: TwinteSession): List<Tag> =
            listTagsResult.getOrThrow()

        override suspend fun createRegisteredCoursesByCodes(
            session: TwinteSession,
            year: Int,
            codes: List<String>,
        ): List<RegisteredCourse> = createRegisteredCoursesByCodesResult.getOrThrow()

        override suspend fun createRegisteredCourseManually(
            session: TwinteSession,
            input: CreateRegisteredCourseInput,
        ): RegisteredCourse = createRegisteredCourseManuallyResult.getOrThrow()

        override suspend fun updateRegisteredCourse(
            session: TwinteSession,
            input: UpdateRegisteredCourseInput,
        ): RegisteredCourse {
            updateCourseOperation()
            return updateRegisteredCourseResult.getOrThrow()
        }

        override suspend fun deleteRegisteredCourse(session: TwinteSession, id: String) {
            deleteRegisteredCourseResult.getOrThrow()
        }

        override suspend fun createTag(session: TwinteSession, name: String): Tag =
            createTagResult.getOrThrow()

        override suspend fun updateTag(session: TwinteSession, id: String, name: String): Tag =
            updateTagResult.getOrThrow()

        override suspend fun deleteTag(session: TwinteSession, id: String) {
            deleteTagResult.getOrThrow()
        }

        override suspend fun rearrangeTags(session: TwinteSession, ids: List<String>): List<Tag> =
            rearrangeTagsResult.getOrThrow()
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
            syncedAt: Instant,
        ) {
            replaceCount += 1
            snapshots.value = TimetableSnapshot(courses, tags, syncedAt)
        }

        override suspend fun upsertCourses(accountId: String, courses: List<RegisteredCourse>) {
            val ids = courses.mapTo(mutableSetOf()) { it.id }
            snapshots.value = snapshots.value.copy(
                courses = snapshots.value.courses.filterNot { it.id in ids } + courses,
            )
        }

        override suspend fun deleteCourse(accountId: String, id: String) {
            snapshots.value = snapshots.value.copy(
                courses = snapshots.value.courses.filterNot { it.id == id },
            )
        }

        override suspend fun upsertTags(accountId: String, tags: List<Tag>) {
            val ids = tags.mapTo(mutableSetOf()) { it.id }
            snapshots.value = snapshots.value.copy(
                tags = snapshots.value.tags.filterNot { it.id in ids } + tags,
            )
        }

        override suspend fun deleteTag(accountId: String, id: String) {
            snapshots.value = snapshots.value.copy(
                courses = snapshots.value.courses.map { it.copy(tagIds = it.tagIds - id) },
                tags = snapshots.value.tags.filterNot { it.id == id },
            )
        }
    }
}

private fun <T> unusedResult(): Result<T> = Result.failure(IllegalStateException("Unused in this test"))

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

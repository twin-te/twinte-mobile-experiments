package net.twinte.mobile_experiments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import net.twinte.mobile_experiments.core.timetable.DailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.core.timetable.TimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableStateHolderTest {
    @Test
    fun selectsCoursesByAcademicYearAndModule() = runTest {
        val timetableService = FakeTimetableService(
            TimetableSnapshot(
                courses = listOf(
                    course(2026, "Spring course", CourseSchedule(Module.SpringA, Day.Monday, 1, "3A204")),
                    course(2026, "Fall course", CourseSchedule(Module.FallA, Day.Tuesday, 2, "1C101")),
                    course(2025, "Previous course", CourseSchedule(Module.SpringA, Day.Wednesday, 3, "5C216")),
                ),
            ),
        )
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )

        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        runCurrent()

        assertEquals(
            "Spring course",
            stateHolder.timetableUiState.value.cells[TimetableSlot(Day.Monday, 1)]?.single()?.name,
        )

        stateHolder.selectModule(Module.FallA)
        assertEquals(
            "Fall course",
            stateHolder.timetableUiState.value.cells[TimetableSlot(Day.Tuesday, 2)]?.single()?.name,
        )

        stateHolder.selectModule(Module.SpringA)
        stateHolder.selectPreviousYear()
        assertEquals(
            "Previous course",
            stateHolder.timetableUiState.value.cells[TimetableSlot(Day.Wednesday, 3)]?.single()?.name,
        )
    }

    @Test
    fun changingDateSwitchesDailyObservation() = runTest {
        val firstDate = LocalDate(2026, 5, 1)
        val secondDate = LocalDate(2026, 5, 2)
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = dailyService,
            initialDate = firstDate,
            scope = backgroundScope,
            onFailure = {},
        )

        stateHolder.bind("user-id", firstDate)
        runCurrent()
        assertEquals(firstDate, stateHolder.dailyTimetableUiState.value.date)

        stateHolder.bind("user-id", secondDate)
        runCurrent()

        assertEquals(secondDate, stateHolder.dailyTimetableUiState.value.date)
        assertEquals(listOf(firstDate, secondDate), dailyService.observedDates)
    }

    @Test
    fun searchesAndRegistersCourseForActiveAccount() = runTest {
        val searchResult = catalogCourse("GB12345", "Distributed Systems")
        val timetableService = FakeTimetableService(searchResults = listOf(searchResult))
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        runCurrent()

        stateHolder.searchCourses("Distributed Systems")
        runCurrent()

        assertEquals(listOf("Distributed", "Systems"), timetableService.lastSearchInput?.keywords)
        assertEquals(listOf(searchResult), stateHolder.courseOperationsUiState.value.searchResults)

        stateHolder.registerCourse(searchResult)
        runCurrent()

        assertEquals(listOf("GB12345"), timetableService.registeredCodes)
        assertEquals(listOf(LocalDate(2026, 5, 1)), dailyService.refreshedDates)
        assertEquals("Registered Distributed Systems", stateHolder.courseOperationsUiState.value.message)
    }

    @Test
    fun keepsCourseMutationSuccessfulWhenDailyRefreshFails() = runTest {
        val searchResult = catalogCourse("GB12345", "Distributed Systems")
        val timetableService = FakeTimetableService(searchResults = listOf(searchResult))
        val dailyService = FakeDailyTimetableService(refreshError = IllegalStateException("offline"))
        val failures = mutableListOf<Throwable>()
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = failures::add,
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        runCurrent()

        stateHolder.registerCourse(searchResult)
        runCurrent()

        assertEquals(listOf("GB12345"), timetableService.registeredCodes)
        assertEquals(
            "Registered Distributed Systems; daily schedule refresh failed",
            stateHolder.courseOperationsUiState.value.message,
        )
        assertEquals(
            "Refresh failed; showing saved daily schedule",
            stateHolder.dailyTimetableUiState.value.message,
        )
        assertEquals(emptyList(), failures)
    }

    @Test
    fun refreshesDailyTimetableAfterUpdatingAndDeletingCourse() = runTest {
        val date = LocalDate(2026, 5, 1)
        val registeredCourse = course(
            year = 2026,
            name = "Distributed Systems",
            schedule = CourseSchedule(Module.SpringA, Day.Monday, 1, "3A204"),
        )
        val timetableService = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = listOf(registeredCourse)),
        )
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", date)
        runCurrent()

        stateHolder.updateCourse(UpdateRegisteredCourseInput(registeredCourse.id, name = "Renamed"))
        runCurrent()
        stateHolder.deleteCourse(registeredCourse)
        runCurrent()

        assertEquals(listOf(registeredCourse.id), timetableService.updatedCourseIds)
        assertEquals(listOf(registeredCourse.id), timetableService.deletedCourseIds)
        assertEquals(listOf(date, date), dailyService.refreshedDates)
    }

    @Test
    fun managesTagsAndSendsCompleteOrder() = runTest {
        val firstTag = Tag("first", "First", 0)
        val secondTag = Tag("second", "Second", 1)
        val timetableService = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(tags = listOf(firstTag, secondTag)),
        )
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        runCurrent()

        assertEquals(listOf(firstTag, secondTag), stateHolder.timetableUiState.value.tags)

        stateHolder.moveTag(firstTag, 1)
        runCurrent()
        stateHolder.createTag("New")
        runCurrent()
        stateHolder.updateTag(firstTag, "Renamed")
        runCurrent()
        stateHolder.deleteTag(secondTag)
        runCurrent()

        assertEquals(listOf("New"), timetableService.createdTagNames)
        assertEquals(listOf("first" to "Renamed"), timetableService.updatedTags)
        assertEquals(listOf("second"), timetableService.deletedTagIds)
        assertEquals(listOf("second", "first"), timetableService.rearrangedTagIds)
        assertEquals(emptyList(), dailyService.refreshedDates)
    }

    @Test
    fun changingYearDiscardsPendingSearchResults() = runTest {
        val pendingResults = CompletableDeferred<List<Course>>()
        val oldCourse = catalogCourse("OLD", "Old year")
        val newCourse = catalogCourse("NEW", "New year").copy(year = 2025)
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(
                searchResultProvider = { input ->
                    if (input.year == 2026) pendingResults.await() else listOf(newCourse)
                },
            ),
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        stateHolder.searchCourses("old")
        runCurrent()

        stateHolder.selectPreviousYear()
        assertFalse(stateHolder.courseOperationsUiState.value.isLoading)
        stateHolder.searchCourses("new")
        runCurrent()
        pendingResults.complete(listOf(oldCourse))
        runCurrent()

        assertEquals(2025, stateHolder.timetableUiState.value.selectedYear)
        assertEquals(listOf(newCourse), stateHolder.courseOperationsUiState.value.searchResults)
    }

    @Test
    fun changingYearDoesNotInterruptMutationOrAllowAnotherOne() = runTest {
        val pendingUpdate = CompletableDeferred<Unit>()
        val registeredCourse = course(2026, "Course", CourseSchedule(Module.SpringA, Day.Monday, 1, "Room"))
        val timetableService = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = listOf(registeredCourse)),
            updateOperation = { pendingUpdate.await() },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        stateHolder.updateCourse(UpdateRegisteredCourseInput(registeredCourse.id, name = "Updated"))
        runCurrent()

        stateHolder.selectPreviousYear()
        assertTrue(stateHolder.courseOperationsUiState.value.isLoading)
        stateHolder.registerCourse(catalogCourse("NEW", "Another course"))
        runCurrent()
        assertEquals(emptyList(), timetableService.registeredCodes)

        pendingUpdate.complete(Unit)
        runCurrent()

        assertEquals(listOf(registeredCourse.id), timetableService.updatedCourseIds)
        assertEquals("Updated Updated", stateHolder.courseOperationsUiState.value.message)
        assertFalse(stateHolder.courseOperationsUiState.value.isLoading)
    }

    @Test
    fun cacheEmissionDoesNotEndPendingRefreshOrAllowDuplicateRequest() = runTest {
        val pendingRefresh = CompletableDeferred<Unit>()
        var requestCount = 0
        val timetableService = FakeTimetableService(
            refreshOperation = {
                requestCount += 1
                pendingRefresh.await()
            },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        runCurrent()
        stateHolder.refreshTimetable()
        runCurrent()
        timetableService.emitSnapshot(TimetableSnapshot(tags = listOf(Tag("id", "New tag", 0))))
        runCurrent()

        assertTrue(stateHolder.timetableUiState.value.isLoading)
        stateHolder.refreshTimetable()
        runCurrent()
        assertEquals(1, requestCount)

        pendingRefresh.complete(Unit)
        runCurrent()
        assertFalse(stateHolder.timetableUiState.value.isLoading)
    }

    @Test
    fun dailyCacheObservationDoesNotEndPendingRefresh() = runTest {
        val pendingRefresh = CompletableDeferred<Unit>()
        val dailyService = FakeDailyTimetableService(refreshOperation = { pendingRefresh.await() })
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = dailyService,
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        stateHolder.refreshDailyTimetable()
        runCurrent()

        assertTrue(stateHolder.dailyTimetableUiState.value.isLoading)
        stateHolder.refreshDailyTimetable()
        runCurrent()
        assertEquals(1, dailyService.refreshedDates.size)

        pendingRefresh.complete(Unit)
        runCurrent()
        assertFalse(stateHolder.dailyTimetableUiState.value.isLoading)
    }

    @Test
    fun previousDatesRefreshFailureDoesNotOverwriteCurrentDaysState() = runTest {
        val pendingRefresh = CompletableDeferred<Unit>()
        val date = LocalDate(2026, 5, 1)
        val nextDate = LocalDate(2026, 5, 2)
        val dailyService = FakeDailyTimetableService(
            refreshOperation = { pendingRefresh.await() },
            refreshError = IllegalStateException("offline"),
        )
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = dailyService,
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", date)
        stateHolder.registerCourse(catalogCourse("NEW", "Course"))
        runCurrent()
        stateHolder.bind("user-id", nextDate)
        runCurrent()

        pendingRefresh.complete(Unit)
        runCurrent()

        assertEquals(nextDate, stateHolder.dailyTimetableUiState.value.date)
        assertEquals("No saved daily schedule", stateHolder.dailyTimetableUiState.value.message)
        assertEquals("Registered Course; daily schedule refresh failed", stateHolder.courseOperationsUiState.value.message)
    }

    @Test
    fun unauthorizedDailyRefreshStillNotifiesAuthAfterSuccessfulMutation() = runTest {
        val error = TwinteApiException(401, "unauthorized")
        val failures = mutableListOf<Throwable>()
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = FakeDailyTimetableService(refreshError = error),
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = failures::add,
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        stateHolder.registerCourse(catalogCourse("NEW", "Course"))
        runCurrent()

        assertSame(error, failures.single())
        assertEquals("Registered Course; daily schedule refresh failed", stateHolder.courseOperationsUiState.value.message)
        assertFalse(stateHolder.courseOperationsUiState.value.isLoading)
    }

    @Test
    fun signingOutCancelsPendingMutationAndClearsItsUiState() = runTest {
        val pendingUpdate = CompletableDeferred<Unit>()
        val registeredCourse = course(2026, "Course", CourseSchedule(Module.SpringA, Day.Monday, 1, "Room"))
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(
                initialSnapshot = TimetableSnapshot(courses = listOf(registeredCourse)),
                updateOperation = { pendingUpdate.await() },
            ),
            dailyTimetableService = dailyService,
            initialDate = LocalDate(2026, 5, 1),
            scope = backgroundScope,
            onFailure = { error("Cancellation must not be reported as a failure") },
        )
        stateHolder.bind("user-id", LocalDate(2026, 5, 1))
        stateHolder.updateCourse(UpdateRegisteredCourseInput(registeredCourse.id, name = "Updated"))
        runCurrent()

        stateHolder.bind(null, LocalDate(2026, 5, 1))
        pendingUpdate.complete(Unit)
        runCurrent()

        assertEquals(CourseOperationsUiState(), stateHolder.courseOperationsUiState.value)
        assertEquals(emptyList(), stateHolder.timetableUiState.value.registeredCourses)
        assertEquals(emptyList(), dailyService.refreshedDates)
    }

    @Test
    fun automaticSyncShowsCachedTimetableBeforeRefreshing() = runTest {
        val date = LocalDate(2026, 5, 1)
        val now = Instant.parse("2026-05-01T12:00:00Z")
        val pendingRefresh = CompletableDeferred<Unit>()
        val cachedCourse = course(2026, "Cached", CourseSchedule(Module.SpringA, Day.Monday, 1, "Room"))
        val service = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(
                courses = listOf(cachedCourse),
                lastSuccessfulSyncAt = now - 10.minutes,
            ),
            refreshOperation = { pendingRefresh.await() },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = service,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
            now = { now },
        )

        stateHolder.bind("user-id", date)
        stateHolder.syncIfStale()
        assertEquals(emptyList(), service.refreshedAccountIds)
        runCurrent()

        assertEquals(listOf("user-id"), service.refreshedAccountIds)
        assertEquals(listOf(cachedCourse), stateHolder.timetableUiState.value.registeredCourses)
        assertTrue(stateHolder.timetableUiState.value.isLoading)

        pendingRefresh.complete(Unit)
        runCurrent()
        assertFalse(stateHolder.timetableUiState.value.isLoading)
    }

    @Test
    fun automaticSyncSkipsFreshCacheUntilItBecomesStale() = runTest {
        val date = LocalDate(2026, 5, 1)
        var now = Instant.parse("2026-05-01T12:00:00Z")
        val cachedAt = now - 2.minutes
        val timetableService = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(lastSuccessfulSyncAt = cachedAt),
        )
        val dailyService = FakeDailyTimetableService(initialSnapshot = dailySnapshot(date, cachedAt))
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
            now = { now },
        )

        stateHolder.bind("user-id", date)
        stateHolder.syncIfStale()
        runCurrent()
        assertEquals(emptyList(), timetableService.refreshedAccountIds)
        assertEquals(emptyList(), dailyService.refreshedDates)

        now += 6.minutes
        stateHolder.syncIfStale()
        runCurrent()
        assertEquals(listOf("user-id"), timetableService.refreshedAccountIds)
        assertEquals(listOf(date), dailyService.refreshedDates)
    }

    @Test
    fun automaticSyncFailureRetainsCachedCoursesAndRetriesOnNextRequest() = runTest {
        val date = LocalDate(2026, 5, 1)
        val now = Instant.parse("2026-05-01T12:00:00Z")
        val cachedCourse = course(2026, "Cached", CourseSchedule(Module.SpringA, Day.Monday, 1, "Room"))
        val failure = IllegalStateException("offline")
        val failures = mutableListOf<Throwable>()
        val service = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(
                courses = listOf(cachedCourse),
                lastSuccessfulSyncAt = now - 10.minutes,
            ),
            refreshOperation = { throw failure },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = service,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = failures::add,
            now = { now },
        )

        stateHolder.bind("user-id", date)
        stateHolder.syncIfStale()
        runCurrent()
        assertEquals(listOf(cachedCourse), stateHolder.timetableUiState.value.registeredCourses)
        assertEquals(now - 10.minutes, stateHolder.timetableUiState.value.lastSuccessfulSyncAt)
        assertFalse(stateHolder.timetableUiState.value.isLoading)

        stateHolder.syncIfStale()
        runCurrent()
        assertEquals(listOf("user-id", "user-id"), service.refreshedAccountIds)
        assertEquals(2, failures.size)
        failures.forEach { assertSame(failure, it) }
    }

    @Test
    fun accountChangeCancelsOldAutomaticRefreshWithoutReplacingNewAccount() = runTest {
        val date = LocalDate(2026, 5, 1)
        val now = Instant.parse("2026-05-01T12:00:00Z")
        val pendingFirstRefresh = CompletableDeferred<Unit>()
        val firstCourse = course(2026, "First", CourseSchedule(Module.SpringA, Day.Monday, 1, "A"))
        val secondCourse = course(2026, "Second", CourseSchedule(Module.SpringA, Day.Tuesday, 2, "B"))
        val service = FakeTimetableService(
            snapshotsByAccount = mapOf(
                "first" to MutableStateFlow(TimetableSnapshot(listOf(firstCourse), lastSuccessfulSyncAt = now - 10.minutes)),
                "second" to MutableStateFlow(TimetableSnapshot(listOf(secondCourse), lastSuccessfulSyncAt = now - 10.minutes)),
            ),
            refreshOperation = { accountId ->
                if (accountId == "first") withContext(NonCancellable) { pendingFirstRefresh.await() }
            },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = service,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = { error("Canceled refresh must not report a failure") },
            now = { now },
        )

        stateHolder.bind("first", date)
        stateHolder.syncIfStale()
        runCurrent()
        stateHolder.bind("second", date)
        stateHolder.syncIfStale()
        runCurrent()
        pendingFirstRefresh.complete(Unit)
        runCurrent()

        assertEquals(listOf("first", "second"), service.refreshedAccountIds)
        assertEquals(listOf(secondCourse), stateHolder.timetableUiState.value.registeredCourses)
        assertFalse(stateHolder.timetableUiState.value.isLoading)
    }

    @Test
    fun dateChangeCancelsOldAutomaticDailyRefresh() = runTest {
        val firstDate = LocalDate(2026, 5, 1)
        val secondDate = LocalDate(2026, 5, 2)
        val pendingFirstRefresh = CompletableDeferred<Unit>()
        val dailyService = FakeDailyTimetableService(
            refreshOperation = { date ->
                if (date == firstDate) withContext(NonCancellable) { pendingFirstRefresh.await() }
            },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = dailyService,
            initialDate = firstDate,
            scope = backgroundScope,
            onFailure = { error("Canceled refresh must not report a failure") },
        )

        stateHolder.bind("user-id", firstDate)
        stateHolder.syncIfStale()
        runCurrent()
        stateHolder.bind("user-id", secondDate)
        stateHolder.syncIfStale()
        runCurrent()
        pendingFirstRefresh.complete(Unit)
        runCurrent()

        assertEquals(listOf(firstDate, secondDate), dailyService.refreshedDates)
        assertEquals(secondDate, stateHolder.dailyTimetableUiState.value.date)
        assertFalse(stateHolder.dailyTimetableUiState.value.isLoading)
    }
}

private class FakeTimetableService(
    initialSnapshot: TimetableSnapshot = TimetableSnapshot(),
    private val searchResults: List<Course> = emptyList(),
    private val searchResultProvider: (suspend (SearchCoursesInput) -> List<Course>)? = null,
    private val refreshOperation: suspend (String) -> Unit = {},
    private val updateOperation: suspend () -> Unit = {},
    private val snapshotsByAccount: Map<String, MutableStateFlow<TimetableSnapshot>> = emptyMap(),
) : TimetableService {
    private val snapshots = MutableStateFlow(initialSnapshot)
    val refreshedAccountIds = mutableListOf<String>()
    var lastSearchInput: SearchCoursesInput? = null
        private set
    val registeredCodes = mutableListOf<String>()
    val updatedCourseIds = mutableListOf<String>()
    val deletedCourseIds = mutableListOf<String>()
    val createdTagNames = mutableListOf<String>()
    val updatedTags = mutableListOf<Pair<String, String>>()
    val deletedTagIds = mutableListOf<String>()
    var rearrangedTagIds: List<String> = emptyList()
        private set

    override suspend fun searchCourses(input: SearchCoursesInput): List<Course> {
        lastSearchInput = input
        return searchResultProvider?.invoke(input) ?: searchResults
    }

    override fun observeTimetable(accountId: String, year: Int?): Flow<TimetableSnapshot> =
        snapshotsByAccount[accountId] ?: snapshots

    override suspend fun refresh(accountId: String, year: Int?) {
        refreshedAccountIds += accountId
        refreshOperation(accountId)
    }

    fun emitSnapshot(snapshot: TimetableSnapshot) {
        snapshots.value = snapshot
    }

    override suspend fun createCoursesByCodes(accountId: String, year: Int, codes: List<String>): List<RegisteredCourse> {
        registeredCodes += codes
        return emptyList()
    }

    override suspend fun createCourseManually(accountId: String, input: CreateRegisteredCourseInput) =
        error("Unused in this test")

    override suspend fun updateCourse(accountId: String, input: UpdateRegisteredCourseInput): RegisteredCourse {
        updatedCourseIds += input.id
        updateOperation()
        val course = snapshots.value.courses.first { it.id == input.id }
        return course.copy(name = input.name ?: course.name)
    }

    override suspend fun deleteCourse(accountId: String, id: String) {
        deletedCourseIds += id
    }

    override suspend fun createTag(accountId: String, name: String): Tag {
        createdTagNames += name
        return Tag("created", name, 0)
    }

    override suspend fun updateTag(accountId: String, id: String, name: String): Tag {
        updatedTags += id to name
        return Tag(id, name, 0)
    }

    override suspend fun deleteTag(accountId: String, id: String) {
        deletedTagIds += id
    }

    override suspend fun rearrangeTags(accountId: String, ids: List<String>): List<Tag> {
        rearrangedTagIds = ids
        return emptyList()
    }
}

private fun course(
    year: Int,
    name: String,
    schedule: CourseSchedule,
): RegisteredCourse =
    RegisteredCourse(
        id = "$year-$name",
        year = year,
        name = name,
        instructors = "",
        credit = "",
        methods = listOf(CourseMethod.FaceToFace),
        schedules = listOf(schedule),
    )

private fun catalogCourse(code: String, name: String): Course =
    Course(
        id = "course-$code",
        year = 2026,
        code = code,
        name = name,
        instructors = "Teacher",
        credit = "1.0",
        overview = "",
        remarks = "",
        recommendedGrades = emptyList(),
        methods = emptyList(),
        schedules = emptyList(),
        hasParseError = false,
        isAnnual = false,
    )

private class FakeDailyTimetableService(
    private val refreshError: Throwable? = null,
    private val refreshOperation: suspend (LocalDate) -> Unit = {},
    private val initialSnapshot: DailyTimetableSnapshot? = null,
) : DailyTimetableService {
    val observedDates = mutableListOf<LocalDate>()
    val refreshedDates = mutableListOf<LocalDate>()

    override fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?> {
        observedDates += date
        return MutableStateFlow(initialSnapshot?.takeIf { it.timetable.date == date })
    }

    override suspend fun refresh(accountId: String, date: LocalDate) {
        refreshedDates += date
        refreshOperation(date)
        refreshError?.let { throw it }
    }
}

private fun dailySnapshot(date: LocalDate, syncedAt: Instant): DailyTimetableSnapshot =
    DailyTimetableSnapshot(
        timetable = DailyTimetable(
            date = date,
            module = Module.SpringA,
            effectiveDay = Day.Friday,
            eventLabel = "Normal",
            slots = emptyList(),
        ),
        lastSuccessfulSyncAt = syncedAt,
    )

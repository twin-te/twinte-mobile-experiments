package net.twinte.mobile_experiments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableStateHolderTest {
    @Test
    fun tagsAreToggledWithOnlyTheTagIdsPatch() = runTest {
        val registeredCourse = course(2026, "Seminar", CourseSchedule(Module.FallB, Day.Monday, 2, ""))
            .copy(tagIds = listOf("first"))
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(
            courses = listOf(registeredCourse), tags = listOf(Tag("first", "First", 0), Tag("second", "Second", 1)),
        ))
        val holder = TimetableStateHolder(service, FakeDailyTimetableService(), LocalDate(2026, 11, 6), backgroundScope, {})
        holder.bind("user", LocalDate(2026, 11, 6))
        runCurrent()
        holder.toggleCourseTag(registeredCourse.id, "second")
        runCurrent()
        assertEquals(UpdateRegisteredCourseInput(registeredCourse.id, tagIds = listOf("first", "second")), service.updatedInputs[0])
        holder.toggleCourseTag(registeredCourse.id, "first")
        runCurrent()
        assertEquals(listOf("second"), service.updatedInputs[1].tagIds)
        holder.toggleCourseTag(registeredCourse.id, "missing")
        runCurrent()
        assertEquals(2, service.updatedInputs.size)
    }

    @Test
    fun aCreatedTagRemainsAvailableIfItsCourseAssignmentFails() = runTest {
        val registeredCourse = course(2026, "Seminar", CourseSchedule(Module.FallB, Day.Monday, 2, ""))
            .copy(tagIds = listOf("first"))
        val service = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = listOf(registeredCourse), tags = listOf(Tag("first", "First", 0))),
            updateOperation = { error("offline") },
        )
        val holder = TimetableStateHolder(service, FakeDailyTimetableService(), LocalDate(2026, 11, 6), backgroundScope, {})
        holder.bind("user", LocalDate(2026, 11, 6))
        runCurrent()
        var created = false
        holder.createTagForCourse(registeredCourse.id, "  New tag  ") { created = true }
        runCurrent()
        assertTrue(created)
        assertEquals(listOf("New tag"), service.createdTagNames)
        assertEquals(listOf("first", "created"), service.updatedInputs.single().tagIds)
        assertTrue(holder.courseOperationsUiState.value.operationFailed)
        assertEquals(listOf("first"), holder.timetableUiState.value.registeredCourses.single().tagIds)
        assertTrue(holder.timetableUiState.value.tags.any { it.id == "created" })
    }

    @Test
    fun changingSelectionDoesNotMarkTheCacheLoadedBeforeItsFirstEmission() = runTest {
        val holder = TimetableStateHolder(
            FakeTimetableService(), FakeDailyTimetableService(), LocalDate(2026, 11, 6), backgroundScope, {},
        )
        holder.bind("user", LocalDate(2026, 11, 6))
        holder.selectModule(Module.FallA)
        holder.selectPreviousYear()
        assertFalse(holder.timetableUiState.value.isCacheLoaded)
        runCurrent()
        assertTrue(holder.timetableUiState.value.isCacheLoaded)
        holder.bind("other-user", LocalDate(2026, 11, 6))
        assertFalse(holder.timetableUiState.value.isCacheLoaded)
    }

    @Test
    fun successfulDeletionNotifiesBeforeTheDailyRefreshAndFailureDoesNotNotify() = runTest {
        val pendingRefresh = CompletableDeferred<Unit>()
        val registeredCourse = course(2026, "Seminar", CourseSchedule(Module.FallB, Day.Monday, 2, ""))
        var completed = false
        val holder = TimetableStateHolder(
            FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(registeredCourse))),
            FakeDailyTimetableService(refreshOperation = { pendingRefresh.await() }),
            LocalDate(2026, 11, 6), backgroundScope, {},
        )
        holder.bind("user", LocalDate(2026, 11, 6))
        runCurrent()
        holder.deleteCourse(registeredCourse) { completed = true }
        runCurrent()
        assertTrue(completed)
        assertTrue(holder.courseOperationsUiState.value.isLoading)
        pendingRefresh.complete(Unit)
        runCurrent()
        assertFalse(holder.courseOperationsUiState.value.isLoading)

        val failureHolder = TimetableStateHolder(
            FakeTimetableService(deleteOperation = { error("offline") }), FakeDailyTimetableService(),
            LocalDate(2026, 11, 6), backgroundScope, {},
        )
        failureHolder.bind("user", LocalDate(2026, 11, 6))
        runCurrent()
        completed = false
        failureHolder.deleteCourse(registeredCourse) { completed = true }
        runCurrent()
        assertFalse(completed)
        assertTrue(failureHolder.courseOperationsUiState.value.operationFailed)
    }

    @Test
    fun initialModuleWaitsForTheFirstKnownCalendarWithoutChangingSelectedYear() = runTest {
        val date = LocalDate(2026, 11, 7)
        val daily = FakeDailyTimetableService()
        val holder = TimetableStateHolder(
            FakeTimetableService(), daily, date, backgroundScope, {}, initialYear = 2025,
        )
        holder.bind("user", date)
        runCurrent()
        assertNull(holder.moduleForRestoration)
        daily.emitSnapshot(dailySnapshot(date, Instant.fromEpochMilliseconds(0), Module.Unknown))
        runCurrent()
        assertNull(holder.moduleForRestoration)

        daily.emitSnapshot(dailySnapshot(date, Instant.fromEpochMilliseconds(0), Module.FallB))
        runCurrent()
        assertEquals(Module.FallB, holder.timetableUiState.value.selectedModule)
        assertEquals(2025, holder.timetableUiState.value.selectedYear)
        assertEquals(Module.FallB, holder.moduleForRestoration)
    }

    @Test
    fun manualAndRestoredModulesAreNotOverwrittenByCalendarUpdates() = runTest {
        val date = LocalDate(2026, 11, 7)
        listOf(Module.SpringA, Module.FallA).forEach { selected ->
            val daily = FakeDailyTimetableService()
            val holder = TimetableStateHolder(
                FakeTimetableService(), daily, date, backgroundScope, {},
                initialModule = if (selected == Module.FallA) selected else null,
            )
            holder.bind("user", date)
            if (selected == Module.SpringA) holder.selectModule(selected)
            runCurrent()
            daily.emitSnapshot(dailySnapshot(date, Instant.fromEpochMilliseconds(0), Module.FallB))
            runCurrent()
            assertEquals(selected, holder.timetableUiState.value.selectedModule)
            assertEquals(selected, holder.moduleForRestoration)
        }
    }

    @Test
    fun cachedVacationInitializesDisplaySemesterAndLaterUpdatesKeepTheSelection() = runTest {
        val date = LocalDate(2026, 8, 15)
        val daily = FakeDailyTimetableService(
            initialSnapshot = dailySnapshot(date, Instant.fromEpochMilliseconds(0), Module.SummerVacation),
        )
        val holder = TimetableStateHolder(FakeTimetableService(), daily, date, backgroundScope, {})
        holder.bind("user", date)
        runCurrent()
        assertEquals(Module.SpringC, holder.timetableUiState.value.selectedModule)
        assertEquals(Module.SummerVacation, holder.dailyTimetableUiState.value.module)
        assertEquals(Module.SpringC, holder.dailyTimetableUiState.value.currentModule)
        daily.emitSnapshot(dailySnapshot(date, Instant.fromEpochMilliseconds(1), Module.FallA))
        runCurrent()
        assertEquals(Module.SpringC, holder.timetableUiState.value.selectedModule)
    }

    @Test
    fun currentVacationSemesterUsesTheAcademicYear() = runTest {
        val date = LocalDate(2027, 1, 3)
        val holder = TimetableStateHolder(FakeTimetableService(), FakeDailyTimetableService(), date, backgroundScope, {})
        holder.selectPreviousYear()
        holder.selectCurrentModule(Module.WinterVacation)
        assertEquals(2026, holder.timetableUiState.value.selectedYear)
        assertEquals(Module.FallC, holder.timetableUiState.value.selectedModule)
    }

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
    fun currentModuleUsesAcademicYearAndIgnoresUnknownModule() = runTest {
        val date = LocalDate(2026, 1, 15)
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", date)
        stateHolder.selectNextYear()
        stateHolder.selectModule(Module.FallA)

        stateHolder.selectCurrentModule(Module.Unknown)
        assertEquals(2026, stateHolder.timetableUiState.value.selectedYear)
        assertEquals(Module.FallA, stateHolder.timetableUiState.value.selectedModule)

        stateHolder.selectCurrentModule(Module.FallC)
        assertEquals(2025, stateHolder.timetableUiState.value.selectedYear)
        assertEquals(Module.FallC, stateHolder.timetableUiState.value.selectedModule)
    }

    @Test
    fun currentModuleInSameYearDoesNotCancelPendingSearch() = runTest {
        val date = LocalDate(2026, 5, 1)
        val result = catalogCourse("GB12345", "Seminar")
        val pending = CompletableDeferred<List<Course>>()
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(searchResultProvider = { pending.await() }),
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
        )
        stateHolder.bind("user-id", date)
        stateHolder.searchCourses("Seminar")
        runCurrent()

        stateHolder.selectCurrentModule(Module.SpringB)
        assertTrue(stateHolder.courseOperationsUiState.value.isLoading)
        pending.complete(listOf(result))
        runCurrent()

        assertEquals(Module.SpringB, stateHolder.timetableUiState.value.selectedModule)
        assertEquals(listOf(result), stateHolder.courseOperationsUiState.value.searchResults)
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
    fun createsManualCourseAndRefreshesDailySchedule() = runTest {
        val date = LocalDate(2026, 5, 1)
        val input = CreateRegisteredCourseInput(
            year = 2026,
            name = "Seminar",
            instructors = "Teacher",
            credit = "1.0",
            methods = listOf(CourseMethod.FaceToFace),
            schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 1, "3A204")),
        )
        val timetableService = FakeTimetableService()
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = date,
            scope = backgroundScope,
            onFailure = {},
        )
        var completed = false
        stateHolder.bind("user-id", date)
        runCurrent()

        stateHolder.createCourseManually(input) { completed = true }
        runCurrent()

        assertEquals(listOf(input), timetableService.createdManualInputs)
        assertTrue(completed)
        assertEquals(listOf(date), dailyService.refreshedDates)
        assertEquals("Created Seminar", stateHolder.courseOperationsUiState.value.message)
    }

    @Test
    fun failedManualCreationDoesNotCloseEditor() = runTest {
        val date = LocalDate(2026, 5, 1)
        val failure = IllegalStateException("offline")
        val failures = mutableListOf<Throwable>()
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(manualCreateOperation = { throw failure }),
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = failures::add,
        )
        var completed = false
        stateHolder.bind("user-id", date)
        runCurrent()

        stateHolder.createCourseManually(
            CreateRegisteredCourseInput(2026, "Seminar", "Teacher", "1.0", emptyList(), emptyList()),
        ) { completed = true }
        runCurrent()

        assertFalse(completed)
        assertTrue(stateHolder.courseOperationsUiState.value.operationFailed)
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
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

        var updateCompleted = false
        stateHolder.updateCourse(UpdateRegisteredCourseInput(registeredCourse.id, name = "Renamed")) {
            updateCompleted = true
        }
        runCurrent()
        stateHolder.deleteCourse(registeredCourse)
        runCurrent()

        assertEquals(listOf(registeredCourse.id), timetableService.updatedCourseIds)
        assertTrue(updateCompleted)
        assertEquals(listOf(registeredCourse.id), timetableService.deletedCourseIds)
        assertEquals(listOf(date, date), dailyService.refreshedDates)
    }

    @Test
    fun attendanceChangesSendOnlyOneCounterAndRefreshDailySchedule() = runTest {
        val date = LocalDate(2026, 5, 1)
        val course = course(
            year = 2026,
            name = "Seminar",
            schedule = CourseSchedule(Module.SpringA, Day.Monday, 1, "3A204"),
        ).copy(attendance = 2, absence = 1)
        val timetableService = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = listOf(course)),
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

        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Attendance, -1)
        runCurrent()
        timetableService.emitSnapshot(TimetableSnapshot(courses = listOf(course.copy(attendance = 1))))
        runCurrent()
        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Attendance, -1)
        runCurrent()
        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Absence, 1)
        runCurrent()
        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Late, 1)
        runCurrent()

        assertEquals(1, timetableService.updatedInputs[0].attendance)
        assertEquals(null, timetableService.updatedInputs[0].absence)
        assertEquals(0, timetableService.updatedInputs[1].attendance)
        assertEquals(2, timetableService.updatedInputs[2].absence)
        assertEquals(null, timetableService.updatedInputs[2].attendance)
        assertEquals(1, timetableService.updatedInputs[3].late)
        assertEquals(listOf(date, date, date, date), dailyService.refreshedDates)
    }

    @Test
    fun attendanceRejectsInvalidBoundsAndKeepsCachedValueOnFailure() = runTest {
        val date = LocalDate(2026, 5, 1)
        val course = course(
            year = 2026,
            name = "Seminar",
            schedule = CourseSchedule(Module.SpringA, Day.Monday, 1, "3A204"),
        ).copy(late = Int.MAX_VALUE)
        val failure = IllegalStateException("offline")
        val failures = mutableListOf<Throwable>()
        val timetableService = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = listOf(course)),
            updateOperation = { throw failure },
        )
        val stateHolder = TimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = FakeDailyTimetableService(),
            initialDate = date,
            scope = backgroundScope,
            onFailure = failures::add,
        )
        stateHolder.bind("user-id", date)
        runCurrent()

        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Attendance, -1)
        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Late, 1)
        assertEquals(emptyList(), timetableService.updatedInputs)

        stateHolder.changeCourseAttendance(course.id, AttendanceCounter.Absence, 1)
        runCurrent()
        assertEquals(1, timetableService.updatedInputs.single().absence)
        assertEquals(course, stateHolder.timetableUiState.value.registeredCourses.single())
        assertEquals("Operation failed", stateHolder.courseOperationsUiState.value.message)
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
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

internal class FakeTimetableService(
    initialSnapshot: TimetableSnapshot = TimetableSnapshot(),
    private val searchResults: List<Course> = emptyList(),
    private val searchResultProvider: (suspend (SearchCoursesInput) -> List<Course>)? = null,
    private val refreshOperation: suspend (String) -> Unit = {},
    private val manualCreateOperation: suspend () -> Unit = {},
    private val updateOperation: suspend () -> Unit = {},
    private val snapshotsByAccount: Map<String, MutableStateFlow<TimetableSnapshot>> = emptyMap(),
    private val deleteOperation: suspend () -> Unit = {},
    private val createTagOperation: suspend () -> Unit = {},
) : TimetableService {
    private val snapshots = MutableStateFlow(initialSnapshot)
    val refreshedAccountIds = mutableListOf<String>()
    var lastSearchInput: SearchCoursesInput? = null
        private set
    val registeredCodes = mutableListOf<String>()
    val createdManualInputs = mutableListOf<CreateRegisteredCourseInput>()
    val updatedCourseIds = mutableListOf<String>()
    val updatedInputs = mutableListOf<UpdateRegisteredCourseInput>()
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

    override suspend fun createCourseManually(accountId: String, input: CreateRegisteredCourseInput): RegisteredCourse {
        createdManualInputs += input
        manualCreateOperation()
        return RegisteredCourse(
            id = "manual-id",
            year = input.year,
            name = input.name,
            instructors = input.instructors,
            credit = input.credit,
            methods = input.methods,
            schedules = input.schedules,
        )
    }

    override suspend fun updateCourse(accountId: String, input: UpdateRegisteredCourseInput): RegisteredCourse {
        updatedCourseIds += input.id
        updatedInputs += input
        updateOperation()
        val course = snapshots.value.courses.first { it.id == input.id }
        val updated = course.copy(
            name = input.name ?: course.name,
            instructors = input.instructors ?: course.instructors,
            credit = input.credit ?: course.credit,
            methods = input.methods ?: course.methods,
            schedules = input.schedules ?: course.schedules,
            memo = input.memo ?: course.memo,
            tagIds = input.tagIds ?: course.tagIds,
            attendance = input.attendance ?: course.attendance,
            absence = input.absence ?: course.absence,
            late = input.late ?: course.late,
        )
        snapshots.value = snapshots.value.copy(courses = snapshots.value.courses.map { if (it.id == input.id) updated else it })
        return updated
    }

    override suspend fun deleteCourse(accountId: String, id: String) {
        deletedCourseIds += id
        deleteOperation()
        snapshots.value = snapshots.value.copy(courses = snapshots.value.courses.filterNot { it.id == id })
    }

    override suspend fun createTag(accountId: String, name: String): Tag {
        createdTagNames += name
        createTagOperation()
        val tag = Tag("created", name, 0)
        snapshots.value = snapshots.value.copy(tags = snapshots.value.tags.filterNot { it.id == tag.id } + tag)
        return tag
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

internal class FakeDailyTimetableService(
    private val refreshError: Throwable? = null,
    private val refreshOperation: suspend (LocalDate) -> Unit = {},
    private val initialSnapshot: DailyTimetableSnapshot? = null,
) : DailyTimetableService {
    private val snapshots = MutableStateFlow(initialSnapshot)
    val observedDates = mutableListOf<LocalDate>()
    val refreshedDates = mutableListOf<LocalDate>()

    override fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?> {
        observedDates += date
        return snapshots.map { snapshot -> snapshot?.takeIf { it.timetable.date == date } }
    }

    fun emitSnapshot(snapshot: DailyTimetableSnapshot?) {
        snapshots.value = snapshot
    }

    override suspend fun refresh(accountId: String, date: LocalDate) {
        refreshedDates += date
        refreshOperation(date)
        refreshError?.let { throw it }
    }
}

private fun dailySnapshot(date: LocalDate, syncedAt: Instant, module: Module = Module.SpringA): DailyTimetableSnapshot =
    DailyTimetableSnapshot(
        timetable = DailyTimetable(
            date = date,
            module = module,
            effectiveDay = Day.Friday,
            eventLabel = "Normal",
            slots = emptyList(),
        ),
        lastSuccessfulSyncAt = syncedAt,
    )

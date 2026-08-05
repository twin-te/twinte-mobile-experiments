package net.twinte.mobile_experiments

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.core.timetable.TimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

private class FakeTimetableService(
    initialSnapshot: TimetableSnapshot = TimetableSnapshot(),
    private val searchResults: List<Course> = emptyList(),
) : TimetableService {
    private val snapshots = MutableStateFlow(initialSnapshot)
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
        return searchResults
    }

    override fun observeTimetable(accountId: String, year: Int?): Flow<TimetableSnapshot> =
        snapshots

    override suspend fun refresh(accountId: String, year: Int?) = Unit

    override suspend fun createCoursesByCodes(accountId: String, year: Int, codes: List<String>): List<RegisteredCourse> {
        registeredCodes += codes
        return emptyList()
    }

    override suspend fun createCourseManually(accountId: String, input: CreateRegisteredCourseInput) =
        error("Unused in this test")

    override suspend fun updateCourse(accountId: String, input: UpdateRegisteredCourseInput): RegisteredCourse {
        updatedCourseIds += input.id
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
) : DailyTimetableService {
    val observedDates = mutableListOf<LocalDate>()
    val refreshedDates = mutableListOf<LocalDate>()

    override fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?> {
        observedDates += date
        return MutableStateFlow(null)
    }

    override suspend fun refresh(accountId: String, date: LocalDate) {
        refreshedDates += date
        refreshError?.let { throw it }
    }
}

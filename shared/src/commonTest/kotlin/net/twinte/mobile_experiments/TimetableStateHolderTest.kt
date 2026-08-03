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
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
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
}

private class FakeTimetableService(
    initialSnapshot: TimetableSnapshot = TimetableSnapshot(),
) : TimetableService {
    private val snapshots = MutableStateFlow(initialSnapshot)

    override fun observeTimetable(accountId: String, year: Int?): Flow<TimetableSnapshot> =
        snapshots

    override suspend fun refresh(accountId: String, year: Int?) = Unit
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

private class FakeDailyTimetableService : DailyTimetableService {
    val observedDates = mutableListOf<LocalDate>()

    override fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?> {
        observedDates += date
        return MutableStateFlow(null)
    }

    override suspend fun refresh(accountId: String, date: LocalDate) = Unit
}

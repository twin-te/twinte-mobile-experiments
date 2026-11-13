package net.twinte.mobile_experiments

import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.feature.course.search.CourseSearchDraft
import net.twinte.mobile_experiments.feature.course.search.CourseSearchSchedule
import net.twinte.mobile_experiments.feature.course.search.conflictingCourses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CourseSearchDraftTest {
    @Test
    fun parsesCodeInclusionsExclusionsAndUnicodeWhitespace() {
        val input = CourseSearchDraft(code = " GA\tGB　-0 -GF - ", keyword = "情報　倫理\n社会").toSearchInput(2026, emptyList())
        assertEquals(listOf("GA", "GB"), input.codePrefixesIncluded)
        assertEquals(listOf("0", "GF"), input.codePrefixesExcluded)
        assertEquals(listOf("情報", "倫理", "社会"), input.keywords)
        assertEquals(50, input.limit)
        assertEquals(0, input.offset)
        assertEquals(emptyList(), input.schedulesFullyIncluded)
        assertEquals(emptyList(), input.schedulesPartiallyOverlapped)
    }

    @Test
    fun partiallySpecifiedSchedulesExpandWithoutIntroducingAnUnrestrictedFilter() {
        val input = CourseSearchDraft(schedules = listOf(
            CourseSearchSchedule(), CourseSearchSchedule(Module.SpringB, Day.Monday, 2),
            CourseSearchSchedule(Module.SpringB, Day.Monday, 2),
        )).toSearchInput(2026, emptyList())
        assertEquals(listOf(CourseSchedule(Module.SpringB, Day.Monday, 2, "")), input.schedulesPartiallyOverlapped)
        val module = CourseSearchSchedule(module = Module.SpringA).expand()
        assertEquals(60, module.size)
        assertTrue(module.contains(CourseSchedule(Module.SpringA, Day.Intensive, 0, "")))
        assertEquals(56, CourseSearchSchedule(period = 2).expand().size)
        assertTrue(CourseSearchSchedule(day = Day.Intensive).expand().all { it.period == 0 })
    }

    @Test
    fun blankSearchUsesFullyIncludedSlotsAndDoesNotExcludeSpecialCourses() {
        val occupied = CourseSchedule(Module.FallB, Day.Monday, 2, "3A204")
        val intensive = occupied.copy(day = Day.Intensive, period = 0)
        val course = RegisteredCourse("registered", 2026, "授業", "教員", "1.0", emptyList(), listOf(occupied, intensive))
        val input = CourseSearchDraft(onlyBlank = true).toSearchInput(2026, listOf(course))
        assertFalse(input.schedulesFullyIncluded.contains(occupied.copy(locations = "")))
        assertTrue(input.schedulesFullyIncluded.contains(intensive.copy(locations = "")))
        assertEquals(emptyList(), input.schedulesPartiallyOverlapped)
        val otherYear = CourseSearchDraft(onlyBlank = true).toSearchInput(2025, listOf(course))
        assertTrue(otherYear.schedulesFullyIncluded.contains(occupied.copy(locations = "")))
    }

    @Test
    fun duplicateChecksIncludeTheSelectionButIgnoreSpecialSchedulesAndOtherYears() {
        val first = searchTestCourse("GB1")
        val second = searchTestCourse("GB2")
        val selected = CourseSearchDraft().toggle(first).toggle(second).selected
        assertEquals(listOf("GB1", "GB2"), conflictingCourses(selected, emptyList()).map { it.code })
        val special = first.copy(schedules = listOf(first.schedules.single().copy(day = Day.Intensive, period = 0)))
        val specialSelected = CourseSearchDraft().toggle(special).toggle(special.copy(code = "GB3")).selected
        assertEquals(emptyList(), conflictingCourses(specialSelected, emptyList()))
        val registered = RegisteredCourse("registered", 2025, "授業", "", "1.0", emptyList(), first.schedules)
        assertEquals(emptyList(), conflictingCourses(selected.take(1), listOf(registered)))
        assertEquals(1, conflictingCourses(selected.take(1), listOf(registered.copy(year = 2026))).size)
    }

    @Test
    fun selectionSurvivesRestorationWithoutSerializingLongDescriptions() {
        val course = searchTestCourse("GB1").copy(overview = "長い概要".repeat(1000))
        val draft = CourseSearchDraft(code = "GB", keyword = "情報", onlyBlank = true).toggle(course)
        val restored = decodeFromSavedState<CourseSearchDraft>(encodeToSavedState(draft))
        assertEquals(draft, restored)
        assertEquals(course.schedules, restored.selected.single().schedules)
        assertEquals(emptyList(), restored.toggle(course.copy(id = "another-id")).selected)
        assertEquals(emptyList(), restored.deselect(course.code).selected)
    }
}

internal fun searchTestCourse(code: String, year: Int = 2026, day: Day = Day.Monday, period: Int = 1) = Course(
    id = "catalog-$year-$code", year = year, code = code, name = "情報学演習 $code", instructors = "筑波 太郎", credit = "1.0",
    overview = "情報社会の課題について学び、演習を通じて理解を深める。調査と発表を行う。",
    remarks = "履修上の注意事項", recommendedGrades = listOf(2, 3), methods = listOf(CourseMethod.FaceToFace),
    schedules = listOf(CourseSchedule(Module.FallB, day, period, "3A204")), hasParseError = false, isAnnual = false,
)

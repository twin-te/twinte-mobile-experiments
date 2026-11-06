package net.twinte.mobile_experiments

import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseEditorDraftTest {
    private val monday = CourseSchedule(Module.SpringA, Day.Monday, 1, "3A204")

    @Test
    fun createsManualCourseFromValidDraft() {
        val draft = CourseEditorDraft(
            year = 2026,
            name = " Seminar ",
            instructors = " Teacher ",
            credit = "1.5",
            methods = setOf(CourseMethod.FaceToFace),
            schedules = listOf(monday),
        )

        assertTrue(draft.isValid)
        val input = draft.toCreateInput()
        assertEquals(2026, input.year)
        assertEquals("Seminar", input.name)
        assertEquals("Teacher", input.instructors)
        assertEquals("1.5", input.credit)
        assertEquals(listOf(CourseMethod.FaceToFace), input.methods)
        assertEquals(listOf(monday), input.schedules)
    }

    @Test
    fun editingPreservesScheduleMethodsMemoAndTagsWithoutTouchingAttendance() {
        val course = RegisteredCourse(
            id = "course-id",
            year = 2026,
            name = "Seminar",
            instructors = "Teacher",
            credit = "1.0",
            methods = listOf(CourseMethod.FaceToFace),
            schedules = listOf(monday),
            memo = "Bring notes",
            tagIds = listOf("tag-id"),
            attendance = 2,
        )

        val draft = CourseEditorDraft.fromCourse(course)
        val input = draft.toUpdateInput(course.id)

        assertEquals(course.name, input.name)
        assertEquals(course.methods, input.methods)
        assertEquals(course.schedules, input.schedules)
        assertEquals(course.memo, input.memo)
        assertEquals(course.tagIds, input.tagIds)
        assertNull(input.attendance)
    }

    @Test
    fun rejectsInvalidCreditAndSchedules() {
        val draft = CourseEditorDraft(2026, name = "Seminar", credit = "1.0")
        assertTrue(draft.isValid)
        assertFalse(draft.copy(name = " ").isValid)
        assertFalse(draft.copy(credit = "1").isValid)
        assertFalse(draft.copy(credit = "1.2").isValid)
        assertFalse(draft.copy(schedules = listOf(monday, monday)).isValid)
        assertFalse(draft.copy(schedules = listOf(monday.copy(period = 9))).isValid)
        assertFalse(draft.copy(schedules = listOf(monday.copy(module = Module.WinterVacation))).isValid)
        assertFalse(draft.copy(schedules = listOf(monday.copy(day = Day.Intensive))).isValid)
        assertTrue(draft.copy(schedules = listOf(monday.copy(day = Day.Intensive, period = 0))).isValid)
    }
}

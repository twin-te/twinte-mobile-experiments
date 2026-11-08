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
            instructors = listOf(" Teacher "),
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

    @Test
    fun replacingASchedulePreservesOtherSlotsAndReportsDuplicateSlots() {
        val second = monday.copy(module = Module.FallA, locations = "1E101")
        val draft = CourseEditorDraft(2026, name = "Seminar", credit = "1.0", schedules = listOf(monday, second))
        val changed = draft.replaceSchedule(1, second.copy(day = Day.Intensive, period = 0))
        assertEquals(monday, changed.schedules.first())
        assertEquals("1E101", changed.schedules.last().locations)
        assertTrue(changed.isValid)
        assertFalse(changed.hasDuplicateSchedules)
        assertTrue(draft.replaceSchedule(1, monday.copy(locations = "elsewhere")).hasDuplicateSchedules)
    }

    @Test
    fun parsesTeachersAndRoomAssignmentsUsingTheWebConvertersConvention() {
        val schedules = listOf(monday.copy(locations = "3A204, 1E101"), monday.copy(module = Module.FallA, locations = "3A204"))
        val course = RegisteredCourse(
            id = "course", year = 2026, name = "Seminar", credit = "1.0",
            instructors = "Teacher A、Teacher B, Teacher A", schedules = schedules, methods = emptyList(),
        )
        val draft = CourseEditorDraft.fromCourse(course)
        assertEquals(listOf("Teacher A", "Teacher B"), draft.instructors)
        assertEquals(listOf(CourseEditorRoom("3A204", setOf(0, 1)), CourseEditorRoom("1E101", setOf(0))), draft.rooms)
        val input = draft.toUpdateInput(course.id)
        assertEquals("Teacher A,Teacher B", input.instructors)
        assertEquals(listOf("3A204,1E101", "3A204"), input.schedules!!.map { it.locations })
    }

    @Test
    fun deletingAScheduleShiftsRoomAssignmentsWithoutAssigningTheWrongSlot() {
        val draft = CourseEditorDraft(
            year = 2026, name = "Seminar", credit = "1.0",
            schedules = listOf(monday, monday.copy(module = Module.FallA)),
            rooms = listOf(CourseEditorRoom("Room A", setOf(0, 1)), CourseEditorRoom("Room B", setOf(1))),
        )
        val changed = draft.removeSchedule(0)
        assertEquals(listOf(setOf(0), setOf(0)), changed.rooms.map { it.scheduleIndices })
        assertEquals("Room A,Room B", changed.toCreateInput().schedules.single().locations)
        assertFalse(draft.copy(instructors = listOf("")).isValid)
        assertFalse(draft.copy(instructors = listOf("Teacher", " Teacher ")).isValid)
        assertFalse(draft.copy(rooms = listOf(CourseEditorRoom("", setOf(0)))).isValid)
        assertFalse(draft.copy(rooms = listOf(CourseEditorRoom("Room", emptySet()))).isValid)
        assertFalse(draft.copy(rooms = listOf(CourseEditorRoom("Room", setOf(2)))).isValid)
    }
}

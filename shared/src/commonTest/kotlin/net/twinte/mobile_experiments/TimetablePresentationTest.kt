package net.twinte.mobile_experiments

import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.feature.timetable.specialCourseGroups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TimetablePresentationTest {
    @Test
    fun specialCoursesKeepTheirModulesAndLocationsWithoutDuplicatingTheCourse() {
        val course = course("intensive").copy(schedules = listOf(
            CourseSchedule(Module.SummerVacation, Day.Intensive, 0, "3A204"),
            CourseSchedule(Module.FallA, Day.Intensive, 0, "3A204"),
        ))
        val group = specialCourseGroups(listOf(course)).first { it.label == "集中" }
        assertEquals(listOf("intensive"), group.courses.map { it.id })
        assertEquals(listOf("夏休み", "秋A"), group.courses.single().moduleLabels)
        assertEquals("3A204", group.courses.single().locations)
    }

    @Test
    fun specialCourseModulesAreGroupedBySeasonWithoutLosingVacations() {
        val course = course("intensive").copy(schedules = listOf(
            CourseSchedule(Module.FallC, Day.Intensive, 0, ""),
            CourseSchedule(Module.SpringB, Day.Intensive, 0, ""),
            CourseSchedule(Module.SpringA, Day.Intensive, 0, ""),
            CourseSchedule(Module.SpringA, Day.Intensive, 0, ""),
            CourseSchedule(Module.WinterVacation, Day.Intensive, 0, ""),
            CourseSchedule(Module.SummerVacation, Day.Intensive, 0, ""),
            CourseSchedule(Module.SpringVacation, Day.Intensive, 0, ""),
        ))
        assertEquals(
            listOf("春AB", "夏休み", "秋C", "冬休み", "春休み"),
            specialCourseGroups(listOf(course)).first().courses.single().moduleLabels,
        )
    }

    @Test
    fun coursesOutsideTheStandardGridRemainAvailableUnderOthers() {
        val regular = course("regular").copy(
            schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 1, "")),
        )
        val saturday = course("saturday").copy(
            schedules = listOf(CourseSchedule(Module.SpringA, Day.Saturday, 1, "")),
        )
        val evening = course("evening").copy(
            schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 7, "")),
        )
        val unscheduled = course("unscheduled")
        val group = specialCourseGroups(listOf(regular, saturday, evening, unscheduled))
            .first { it.label == "その他" }
        assertEquals(listOf("saturday", "evening", "unscheduled"), group.courses.map { it.id })
        assertFalse(group.courses.any { it.id == "regular" })
    }

    private fun course(id: String) = RegisteredCourse(
        id, 2026, id, "", "1.0", emptyList(), emptyList(),
    )
}

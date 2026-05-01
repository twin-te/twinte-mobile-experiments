package net.twinte.mobile_experiments.core.timetable

import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DailyTimetableBuilderTest {
    private val builder = DailyTimetableBuilder()

    @Test
    fun usesSubstituteDayWhenEventHasChangeTo() {
        val timetable = builder.build(
            DailyTimetableInput(
                date = "2026-05-01",
                day = Day.Friday,
                module = Module.SpringA,
                events = listOf(
                    SchoolCalendarEvent(
                        id = 1,
                        type = EventType.SubstituteDay,
                        date = "2026-05-01",
                        description = "",
                        changeTo = Day.Monday,
                    ),
                ),
                registeredCourses = listOf(
                    course(
                        id = "course-1",
                        name = "情報科学",
                        schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 2, "3A204")),
                    ),
                    course(
                        id = "course-2",
                        name = "金曜の授業",
                        schedules = listOf(CourseSchedule(Module.SpringA, Day.Friday, 2, "1C101")),
                    ),
                ),
            ),
        )

        assertEquals(Day.Monday, timetable.effectiveDay)
        assertEquals("月曜日課", timetable.eventLabel)
        val slot = assertIs<DailyCourseSlot.Course>(timetable.slots[1])
        assertEquals("情報科学", slot.name)
        assertEquals("10:10", slot.startTime)
    }

    @Test
    fun marksDuplicatedCoursesInSamePeriod() {
        val timetable = builder.build(
            DailyTimetableInput(
                date = "2026-05-01",
                day = Day.Friday,
                module = Module.SpringA,
                events = emptyList(),
                registeredCourses = listOf(
                    course(
                        id = "course-1",
                        name = "A",
                        schedules = listOf(CourseSchedule(Module.SpringA, Day.Friday, 1, "3A204")),
                    ),
                    course(
                        id = "course-2",
                        name = "B",
                        schedules = listOf(CourseSchedule(Module.SpringA, Day.Friday, 1, "1C101")),
                    ),
                ),
            ),
        )

        val slot = assertIs<DailyCourseSlot.Course>(timetable.slots.first())
        assertEquals("授業が重複しています", slot.name)
        assertEquals(true, slot.isDuplicated)
    }

    private fun course(
        id: String,
        name: String,
        schedules: List<CourseSchedule>,
    ): RegisteredCourse =
        RegisteredCourse(
            id = id,
            year = 2026,
            name = name,
            instructors = "",
            credit = "",
            methods = listOf(CourseMethod.FaceToFace),
            schedules = schedules,
        )
}

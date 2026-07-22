package net.twinte.mobile_experiments.core.timetable

import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent

data class DailyTimetableInput(
    val date: String,
    val day: Day,
    val module: Module,
    val events: List<SchoolCalendarEvent>,
    val registeredCourses: List<RegisteredCourse>,
)

data class DailyTimetable(
    val date: String,
    val module: Module,
    val effectiveDay: Day,
    val eventLabel: String,
    val slots: List<DailyCourseSlot>,
) {
    val lectureCount: Int = slots.count { it is DailyCourseSlot.Course }
}

sealed interface DailyCourseSlot {
    val period: Int
    val startTime: String
    val endTime: String

    data class Empty(
        override val period: Int,
        override val startTime: String,
        override val endTime: String,
    ) : DailyCourseSlot

    data class Course(
        override val period: Int,
        override val startTime: String,
        override val endTime: String,
        val courseId: String,
        val name: String,
        val room: String,
        val isDuplicated: Boolean = false,
    ) : DailyCourseSlot
}

class DailyTimetableBuilder {
    fun build(input: DailyTimetableInput): DailyTimetable {
        val substituteDay = input.events.firstNotNullOfOrNull { it.changeTo }
        val effectiveDay = substituteDay ?: input.day
        val eventLabel = input.events.firstOrNull()?.let { event ->
            when {
                event.changeTo != null -> "${event.changeTo.label}日課"
                event.type == EventType.Holiday || event.type == EventType.PublicHoliday -> event.description
                else -> event.description.ifBlank { "通常日課" }
            }
        } ?: "通常日課"

        val coursesByPeriod = input.registeredCourses
            .flatMap { course ->
                course.schedules
                    .filter { it.module == input.module && it.day == effectiveDay }
                    .map { schedule -> schedule.period to (course to schedule.locations) }
            }
            .groupBy({ it.first }, { it.second })

        val slots = PeriodTimes.regular.map { periodTime ->
            val period = periodTime.period
            val start = periodTime.start.toString()
            val end = periodTime.end.toString()
            val courses = coursesByPeriod[period].orEmpty()
            when (courses.size) {
                0 -> DailyCourseSlot.Empty(period, start, end)
                1 -> {
                    val (course, room) = courses.single()
                    DailyCourseSlot.Course(
                        period = period,
                        startTime = start,
                        endTime = end,
                        courseId = course.id,
                        name = course.name,
                        room = room,
                    )
                }
                else -> DailyCourseSlot.Course(
                    period = period,
                    startTime = start,
                    endTime = end,
                    courseId = "",
                    name = "授業が重複しています",
                    room = "",
                    isDuplicated = true,
                )
            }
        }

        return DailyTimetable(
            date = input.date,
            module = input.module,
            effectiveDay = effectiveDay,
            eventLabel = eventLabel,
            slots = slots,
        )
    }
}

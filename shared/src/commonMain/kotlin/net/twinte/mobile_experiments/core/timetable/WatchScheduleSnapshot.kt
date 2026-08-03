package net.twinte.mobile_experiments.core.timetable

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class WatchScheduleSnapshot(
    val date: LocalDate,
    val moduleLabel: String,
    val dayLabel: String,
    val eventLabel: String,
    val current: WatchCourse?,
    val next: WatchCourse?,
    val courses: List<WatchCourse>,
    val syncedAt: Instant,
)

data class WatchCourse(
    val period: Int,
    val startTime: String,
    val endTime: String,
    val name: String,
    val room: String,
)

class WatchScheduleSnapshotBuilder {
    fun build(
        timetable: DailyTimetable,
        currentPeriod: Int,
        syncedAt: Instant,
    ): WatchScheduleSnapshot {
        val courses = timetable.slots.mapNotNull { slot ->
            val course = slot as? DailyCourseSlot.Course ?: return@mapNotNull null
            WatchCourse(
                period = course.period,
                startTime = course.startTime,
                endTime = course.endTime,
                name = course.name,
                room = course.room,
            )
        }
        return WatchScheduleSnapshot(
            date = timetable.date,
            moduleLabel = timetable.module.label,
            dayLabel = timetable.effectiveDay.label,
            eventLabel = timetable.eventLabel,
            current = courses.firstOrNull { it.period == currentPeriod },
            next = courses.firstOrNull { it.period > currentPeriod },
            courses = courses,
            syncedAt = syncedAt,
        )
    }
}

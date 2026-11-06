package net.twinte.mobile_experiments.feature.timetable

import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse

internal val timetableModules = listOf(
    Module.SpringA, Module.SpringB, Module.SpringC, Module.FallA, Module.FallB, Module.FallC,
)
internal val timetableDays = listOf(Day.Monday, Day.Tuesday, Day.Wednesday, Day.Thursday, Day.Friday)

internal data class SpecialCourseItem(
    val id: String,
    val name: String,
    val modules: String,
    val locations: String,
)

internal data class SpecialCourseGroup(val label: String, val courses: List<SpecialCourseItem>)

internal fun specialCourseGroups(courses: List<RegisteredCourse>): List<SpecialCourseGroup> {
    val specialDays = listOf(Day.Intensive, Day.Appointment, Day.AnyTime, Day.Nt)
    val groups = specialDays.map { day ->
        SpecialCourseGroup(day.label, courses.mapNotNull { course ->
            val schedules = course.schedules.filter { it.day == day }
            if (schedules.isEmpty()) null else SpecialCourseItem(
                course.id, course.name,
                schedules.map { it.module.label }.distinct().joinToString("・"),
                schedules.map { it.locations }.filter(String::isNotBlank).distinct().joinToString(" / "),
            )
        })
    }
    val others = courses.mapNotNull { course ->
        val schedules = course.schedules.filter {
            it.day !in specialDays &&
                (it.day !in timetableDays || it.period !in 1..6 || it.module !in timetableModules)
        }
        if (schedules.isEmpty() && course.schedules.isNotEmpty()) null else SpecialCourseItem(
            course.id, course.name,
            schedules.map { it.module.label }.distinct().joinToString("・"),
            schedules.map { it.locations }.filter(String::isNotBlank).distinct().joinToString(" / "),
        )
    }
    return if (others.isEmpty()) groups else groups + SpecialCourseGroup("その他", others)
}

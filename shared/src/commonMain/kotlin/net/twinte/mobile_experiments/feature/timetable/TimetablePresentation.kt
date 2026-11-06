package net.twinte.mobile_experiments.feature.timetable

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse

internal val timetableModules = listOf(
    Module.SpringA, Module.SpringB, Module.SpringC, Module.FallA, Module.FallB, Module.FallC,
)
internal val timetableDays = listOf(Day.Monday, Day.Tuesday, Day.Wednesday, Day.Thursday, Day.Friday)

internal fun calendarModuleToTimetableModule(module: Module?, date: LocalDate): Module? = when (module) {
    null, Module.Unknown -> null
    Module.SummerVacation -> Module.SpringC
    Module.WinterVacation -> if (date.month == Month.DECEMBER) Module.FallB else Module.FallC
    Module.SpringVacation -> if (date.month == Month.MARCH) Module.FallC else Module.SpringA
    else -> module
}

internal data class SpecialCourseItem(
    val id: String,
    val name: String,
    val moduleLabels: List<String>,
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
                specialCourseModuleLabels(schedules.map { it.module }),
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
            specialCourseModuleLabels(schedules.map { it.module }),
            schedules.map { it.locations }.filter(String::isNotBlank).distinct().joinToString(" / "),
        )
    }
    return if (others.isEmpty()) groups else groups + SpecialCourseGroup("その他", others)
}

private fun specialCourseModuleLabels(modules: List<Module>): List<String> = buildList {
    val spring = listOf(Module.SpringA, Module.SpringB, Module.SpringC).filter { it in modules }
    val fall = listOf(Module.FallA, Module.FallB, Module.FallC).filter { it in modules }
    if (spring.isNotEmpty()) add("春" + spring.joinToString("") { it.label.removePrefix("春") })
    if (Module.SummerVacation in modules) add(Module.SummerVacation.label)
    if (fall.isNotEmpty()) add("秋" + fall.joinToString("") { it.label.removePrefix("秋") })
    if (Module.WinterVacation in modules) add(Module.WinterVacation.label)
    if (Module.SpringVacation in modules) add(Module.SpringVacation.label)
}

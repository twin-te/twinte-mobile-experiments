package net.twinte.mobile_experiments.feature.course.search

import kotlinx.serialization.Serializable
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.editableDays
import net.twinte.mobile_experiments.editableModules
import net.twinte.mobile_experiments.isRegular

internal const val courseSearchPageSize = 50
private val whitespace = Regex("[\\p{Z}\\s]+")

// Keep saved selections small: descriptions and remarks are only needed in the fetched result list.
@Serializable
internal data class CourseSearchSelection(val year: Int, val code: String, val name: String, val schedules: List<CourseSchedule>)

@Serializable
internal data class CourseSearchSchedule(
    val module: Module? = null,
    val day: Day? = null,
    val period: Int? = null,
) {
    val isUnspecified: Boolean get() = module == null && day == null && period == null
    val label: String get() = listOfNotNull(module?.label, day?.label, period?.let { "${it}限" }).joinToString(" ")

    fun expand(): List<CourseSchedule> = (module?.let(::listOf) ?: editableModules).flatMap { module ->
        (day?.let(::listOf) ?: editableDays).flatMap { day ->
            if (day.isRegular()) (period?.let(::listOf) ?: (1..8).toList()).map { period ->
                CourseSchedule(module, day, period, "")
            } else if (period == null) listOf(CourseSchedule(module, day, 0, "")) else emptyList()
        }
    }
}

@Serializable
internal data class CourseSearchDraft(
    val code: String = "",
    val keyword: String = "",
    val detailed: Boolean = true,
    val onlyBlank: Boolean = false,
    val schedules: List<CourseSearchSchedule> = listOf(CourseSearchSchedule()),
    val selected: List<CourseSearchSelection> = emptyList(),
) {
    val conditionLabel: String
        get() = if (onlyBlank) "空きコマのみ"
        else schedules.filterNot { it.isUnspecified }.joinToString("、") { it.label }.ifEmpty { "未指定" }

    fun toSearchInput(year: Int, registered: List<RegisteredCourse>): SearchCoursesInput {
        val codes = code.trim().split(whitespace).filter(String::isNotEmpty)
        val occupied = registered.filter { it.year == year }.flatMap { it.schedules }
            .filter { it.day.isRegular() }.mapTo(mutableSetOf(), ::scheduleKey)
        val blank = if (onlyBlank) CourseSearchSchedule().expand().filter { scheduleKey(it) !in occupied } else emptyList()
        return SearchCoursesInput(
            year = year,
            keywords = keyword.trim().split(whitespace).filter(String::isNotEmpty),
            codePrefixesIncluded = codes.filterNot { it.startsWith("-") },
            codePrefixesExcluded = codes.filter { it.startsWith("-") }.map { it.drop(1) }.filter(String::isNotEmpty),
            schedulesFullyIncluded = blank,
            schedulesPartiallyOverlapped = if (onlyBlank) emptyList()
                else schedules.filterNot { it.isUnspecified }.flatMap { it.expand() }.distinct(),
            limit = courseSearchPageSize,
        )
    }

    fun toggle(course: Course): CourseSearchDraft = copy(
        selected = if (selected.any { it.code == course.code }) selected.filterNot { it.code == course.code }
        else selected + CourseSearchSelection(course.year, course.code, course.name, course.schedules),
    )

    fun deselect(code: String): CourseSearchDraft = copy(selected = selected.filterNot { it.code == code })
}

private fun scheduleKey(schedule: CourseSchedule) = Triple(schedule.module, schedule.day, schedule.period)

internal fun conflictingCourses(selected: List<CourseSearchSelection>, registered: List<RegisteredCourse>): List<CourseSearchSelection> =
    selected.filter { course ->
        val otherSlots = (registered.filter { it.year == course.year }.flatMap { it.schedules } +
            selected.filter { it.code != course.code && it.year == course.year }.flatMap { it.schedules })
            .filter { it.day.isRegular() && it.period in 1..8 }.mapTo(mutableSetOf(), ::scheduleKey)
        course.schedules.any { it.day.isRegular() && it.period in 1..8 && scheduleKey(it) in otherSlots }
    }

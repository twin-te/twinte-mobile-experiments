package net.twinte.mobile_experiments

import kotlinx.serialization.Serializable
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

internal val editableModules = listOf(
    Module.SpringA,
    Module.SpringB,
    Module.SpringC,
    Module.SummerVacation,
    Module.FallA,
    Module.FallB,
    Module.FallC,
    Module.SpringVacation,
)

internal val editableDays = listOf(
    Day.Monday,
    Day.Tuesday,
    Day.Wednesday,
    Day.Thursday,
    Day.Friday,
    Day.Saturday,
    Day.Sunday,
    Day.Intensive,
    Day.Appointment,
    Day.AnyTime,
    Day.Nt,
)

private val creditPattern = Regex("\\d{1,2}\\.[05]")
private val regularDays = setOf(
    Day.Monday, Day.Tuesday, Day.Wednesday, Day.Thursday,
    Day.Friday, Day.Saturday, Day.Sunday,
)

internal fun Day.isRegular(): Boolean = this in regularDays

@Serializable
internal data class CourseEditorDraft(
    val year: Int,
    val name: String = "",
    val instructors: String = "",
    val credit: String = "",
    val methods: Set<CourseMethod> = emptySet(),
    val schedules: List<CourseSchedule> = emptyList(),
    val memo: String = "",
    val tagIds: Set<String> = emptySet(),
) {
    val isValid: Boolean
        get() = year > 0 &&
            name.isNotBlank() &&
            creditPattern.matches(credit.trim()) &&
            schedules.all { schedule ->
                schedule.module in editableModules &&
                    schedule.day in editableDays &&
                    (if (schedule.day.isRegular()) schedule.period in 1..8 else schedule.period == 0)
            } &&
            schedules.distinctBy { Triple(it.module, it.day, it.period) }.size == schedules.size

    fun toCreateInput(): CreateRegisteredCourseInput {
        require(isValid)
        return CreateRegisteredCourseInput(
            year = year,
            name = name.trim(),
            instructors = instructors.trim(),
            credit = credit.trim(),
            methods = CourseMethod.entries.filter { it in methods },
            schedules = schedules,
        )
    }

    fun toUpdateInput(id: String): UpdateRegisteredCourseInput {
        require(isValid)
        return UpdateRegisteredCourseInput(
            id = id,
            name = name.trim(),
            instructors = instructors.trim(),
            credit = credit.trim(),
            methods = CourseMethod.entries.filter { it in methods },
            schedules = schedules,
            memo = memo,
            tagIds = tagIds.toList(),
        )
    }

    companion object {
        fun fromCourse(course: RegisteredCourse): CourseEditorDraft =
            CourseEditorDraft(
                year = course.year,
                name = course.name,
                instructors = course.instructors,
                credit = course.credit,
                methods = course.methods.toSet(),
                schedules = course.schedules,
                memo = course.memo,
                tagIds = course.tagIds.toSet(),
            )
    }
}

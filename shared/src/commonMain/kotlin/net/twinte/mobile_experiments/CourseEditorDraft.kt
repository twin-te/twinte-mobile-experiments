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
// Row indices keep room assignments attached when a slot's module, day, or period is edited.
internal data class CourseEditorRoom(val name: String, val scheduleIndices: Set<Int>)

private fun roomsFromSchedules(schedules: List<CourseSchedule>): List<CourseEditorRoom> {
    val assignments = linkedMapOf<String, MutableSet<Int>>()
    schedules.forEachIndexed { index, schedule ->
        schedule.locations.split(',', ' ').map(String::trim).filter(String::isNotEmpty).forEach { name ->
            assignments.getOrPut(name) { linkedSetOf() }.add(index)
        }
    }
    return assignments.map { (name, indices) -> CourseEditorRoom(name, indices) }
}

@Serializable
internal data class CourseEditorDraft(
    val year: Int,
    val name: String = "",
    val instructors: List<String> = emptyList(),
    val credit: String = "",
    val methods: Set<CourseMethod> = emptySet(),
    val schedules: List<CourseSchedule> = emptyList(),
    val rooms: List<CourseEditorRoom> = roomsFromSchedules(schedules),
    val memo: String = "",
    val tagIds: Set<String> = emptySet(),
) {
    val hasDuplicateSchedules: Boolean
        get() = schedules.distinctBy { Triple(it.module, it.day, it.period) }.size != schedules.size

    val isValid: Boolean
        get() = year > 0 &&
            name.isNotBlank() &&
            creditPattern.matches(credit.trim()) &&
            instructors.all { it.isNotBlank() } &&
            instructors.map(String::trim).distinct().size == instructors.size &&
            rooms.all { room ->
                room.name.isNotBlank() && room.scheduleIndices.isNotEmpty() &&
                    room.scheduleIndices.all { it in schedules.indices }
            } &&
            schedules.all { schedule ->
                schedule.module in editableModules &&
                    schedule.day in editableDays &&
                    (if (schedule.day.isRegular()) schedule.period in 1..8 else schedule.period == 0)
            } &&
            !hasDuplicateSchedules

    fun replaceSchedule(index: Int, schedule: CourseSchedule): CourseEditorDraft =
        copy(schedules = schedules.mapIndexed { i, current -> if (i == index) schedule else current })

    fun replaceRoom(index: Int, room: CourseEditorRoom): CourseEditorDraft =
        copy(rooms = rooms.mapIndexed { i, current -> if (i == index) room else current })

    fun removeSchedule(index: Int): CourseEditorDraft = copy(
        schedules = schedules.filterIndexed { i, _ -> i != index },
        rooms = rooms.map { room ->
            room.copy(scheduleIndices = room.scheduleIndices.filter { it != index }.map {
                if (it > index) it - 1 else it
            }.toSet())
        },
    )

    private fun schedulesWithRooms(): List<CourseSchedule> = schedules.mapIndexed { index, schedule ->
        schedule.copy(locations = rooms.filter { index in it.scheduleIndices }.joinToString(",") { it.name.trim() })
    }

    fun toCreateInput(): CreateRegisteredCourseInput {
        require(isValid)
        return CreateRegisteredCourseInput(
            year = year,
            name = name.trim(),
            instructors = instructors.joinToString(",") { it.trim() },
            credit = credit.trim(),
            methods = CourseMethod.entries.filter { it in methods },
            schedules = schedulesWithRooms(),
        )
    }

    fun toUpdateInput(id: String): UpdateRegisteredCourseInput {
        require(isValid)
        return UpdateRegisteredCourseInput(
            id = id,
            name = name.trim(),
            instructors = instructors.joinToString(",") { it.trim() },
            credit = credit.trim(),
            methods = CourseMethod.entries.filter { it in methods },
            schedules = schedulesWithRooms(),
            memo = memo,
            tagIds = tagIds.toList(),
        )
    }

    companion object {
        fun fromCourse(course: RegisteredCourse): CourseEditorDraft =
            CourseEditorDraft(
                year = course.year,
                name = course.name,
                instructors = course.instructors.split(',', '、').map(String::trim).filter(String::isNotEmpty).distinct(),
                credit = course.credit,
                methods = course.methods.toSet(),
                schedules = course.schedules,
                memo = course.memo,
                tagIds = course.tagIds.toSet(),
            )
    }
}

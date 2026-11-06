package net.twinte.mobile_experiments.core.api.ktor

import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.api.timetable.v1.CourseMethod as ApiCourseMethod
import net.twinte.api.timetable.v1.Course as ApiCourse
import net.twinte.api.timetable.v1.Day as ApiDay
import net.twinte.api.timetable.v1.Module as ApiModule
import net.twinte.api.timetable.v1.RegisteredCourse as ApiRegisteredCourse
import net.twinte.api.timetable.v1.Schedule as ApiSchedule
import net.twinte.api.timetable.v1.Tag as ApiTag

internal fun ApiCourse.toDomainCourse(): Course =
    Course(
        id = requireNotNull(id) { "Course.id is missing" }.value,
        year = requireNotNull(year) { "Course.year is missing" }.value,
        code = code,
        name = name,
        instructors = instructors,
        credit = credit,
        overview = overview,
        remarks = remarks,
        recommendedGrades = recommendedGrades,
        methods = methods.map { it.toDomainCourseMethod() },
        schedules = schedules.map { it.toDomainCourseSchedule() },
        hasParseError = hasParseError,
        isAnnual = isAnnual,
    )

internal fun ApiRegisteredCourse.toDomainRegisteredCourse(): RegisteredCourse =
    RegisteredCourse(
        id = requireNotNull(id) { "RegisteredCourse.id is missing" }.value,
        year = requireNotNull(year) { "RegisteredCourse.year is missing" }.value,
        name = name,
        instructors = instructors,
        credit = credit,
        methods = methods.map { it.toDomainCourseMethod() },
        schedules = schedules.map { it.toDomainCourseSchedule() },
        memo = memo,
        attendance = attendance,
        absence = absence,
        late = late,
        tagIds = tagIds.map { it.value },
        code = code,
    )

private fun ApiSchedule.toDomainCourseSchedule(): CourseSchedule =
    CourseSchedule(
        module = module.toDomainModule(),
        day = day.toDomainDay(),
        period = period,
        locations = locations,
    )

internal fun ApiTag.toDomainTag(): Tag =
    Tag(
        id = requireNotNull(id) { "Tag.id is missing" }.value,
        name = name,
        order = order,
    )

private fun ApiModule.toDomainModule(): Module =
    when (this) {
        ApiModule.SPRING_A -> Module.SpringA
        ApiModule.SPRING_B -> Module.SpringB
        ApiModule.SPRING_C -> Module.SpringC
        ApiModule.FALL_A -> Module.FallA
        ApiModule.FALL_B -> Module.FallB
        ApiModule.FALL_C -> Module.FallC
        ApiModule.SUMMER_VACATION -> Module.SummerVacation
        ApiModule.SPRING_VACATION -> Module.SpringVacation
        else -> Module.Unknown
    }

private fun ApiDay.toDomainDay(): Day =
    when (this) {
        ApiDay.SUN -> Day.Sunday
        ApiDay.MON -> Day.Monday
        ApiDay.TUE -> Day.Tuesday
        ApiDay.WED -> Day.Wednesday
        ApiDay.THU -> Day.Thursday
        ApiDay.FRI -> Day.Friday
        ApiDay.SAT -> Day.Saturday
        ApiDay.INTENSIVE -> Day.Intensive
        ApiDay.APPOINTMENT -> Day.Appointment
        ApiDay.ANY_TIME -> Day.AnyTime
        ApiDay.NT -> Day.Nt
        else -> error("Unsupported timetable day: $this")
    }

private fun ApiCourseMethod.toDomainCourseMethod(): CourseMethod =
    when (this) {
        ApiCourseMethod.ONLINE_ASYNCHRONOUS -> CourseMethod.OnlineAsynchronous
        ApiCourseMethod.ONLINE_SYNCHRONOUS -> CourseMethod.OnlineSynchronous
        ApiCourseMethod.FACE_TO_FACE -> CourseMethod.FaceToFace
        ApiCourseMethod.OTHERS -> CourseMethod.Others
        else -> error("Unsupported course method: $this")
    }

internal fun CourseMethod.toApiCourseMethod(): ApiCourseMethod =
    when (this) {
        CourseMethod.OnlineAsynchronous -> ApiCourseMethod.ONLINE_ASYNCHRONOUS
        CourseMethod.OnlineSynchronous -> ApiCourseMethod.ONLINE_SYNCHRONOUS
        CourseMethod.FaceToFace -> ApiCourseMethod.FACE_TO_FACE
        CourseMethod.Others -> ApiCourseMethod.OTHERS
    }

internal fun CourseSchedule.toApiSchedule(): ApiSchedule =
    ApiSchedule(
        module = module.toApiModule(),
        day = day.toApiDay(),
        period = period,
        locations = locations,
    )

private fun Module.toApiModule(): ApiModule =
    when (this) {
        Module.SpringA -> ApiModule.SPRING_A
        Module.SpringB -> ApiModule.SPRING_B
        Module.SpringC -> ApiModule.SPRING_C
        Module.FallA -> ApiModule.FALL_A
        Module.FallB -> ApiModule.FALL_B
        Module.FallC -> ApiModule.FALL_C
        Module.SummerVacation -> ApiModule.SUMMER_VACATION
        Module.SpringVacation -> ApiModule.SPRING_VACATION
        Module.Unknown,
        Module.WinterVacation,
        -> error("Unsupported timetable module: $this")
    }

private fun Day.toApiDay(): ApiDay =
    when (this) {
        Day.Sunday -> ApiDay.SUN
        Day.Monday -> ApiDay.MON
        Day.Tuesday -> ApiDay.TUE
        Day.Wednesday -> ApiDay.WED
        Day.Thursday -> ApiDay.THU
        Day.Friday -> ApiDay.FRI
        Day.Saturday -> ApiDay.SAT
        Day.Intensive -> ApiDay.INTENSIVE
        Day.Appointment -> ApiDay.APPOINTMENT
        Day.AnyTime -> ApiDay.ANY_TIME
        Day.Nt -> ApiDay.NT
    }

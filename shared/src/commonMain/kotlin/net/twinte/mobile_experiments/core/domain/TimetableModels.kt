package net.twinte.mobile_experiments.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class Module(val label: String) {
    Unknown(""),
    SpringA("春A"),
    SpringB("春B"),
    SpringC("春C"),
    SummerVacation("夏休み"),
    FallA("秋A"),
    FallB("秋B"),
    WinterVacation("冬休み"),
    FallC("秋C"),
    SpringVacation("春休み"),
}

@Serializable
enum class Day(val label: String) {
    Sunday("日曜"),
    Monday("月曜"),
    Tuesday("火曜"),
    Wednesday("水曜"),
    Thursday("木曜"),
    Friday("金曜"),
    Saturday("土曜"),
    Intensive("集中"),
    Appointment("応談"),
    AnyTime("随時"),
    Nt("NT"),
}

@Serializable
enum class CourseMethod {
    OnlineAsynchronous,
    OnlineSynchronous,
    FaceToFace,
    Others,
}

enum class EventType {
    Holiday,
    PublicHoliday,
    Exam,
    SubstituteDay,
    Other,
}

@Serializable
data class CourseSchedule(
    val module: Module,
    val day: Day,
    val period: Int,
    val locations: String,
)

data class Course(
    val id: String,
    val year: Int,
    val code: String,
    val name: String,
    val instructors: String,
    val credit: String,
    val overview: String,
    val remarks: String,
    val recommendedGrades: List<Int>,
    val methods: List<CourseMethod>,
    val schedules: List<CourseSchedule>,
    val hasParseError: Boolean,
    val isAnnual: Boolean,
)

data class SearchCoursesInput(
    val year: Int,
    val keywords: List<String> = emptyList(),
    val codePrefixesIncluded: List<String> = emptyList(),
    val codePrefixesExcluded: List<String> = emptyList(),
    val schedulesFullyIncluded: List<CourseSchedule> = emptyList(),
    val schedulesPartiallyOverlapped: List<CourseSchedule> = emptyList(),
    val limit: Int = 20,
    val offset: Int = 0,
)

data class RegisteredCourse(
    val id: String,
    val year: Int,
    val name: String,
    val instructors: String,
    val credit: String,
    val methods: List<CourseMethod>,
    val schedules: List<CourseSchedule>,
    val memo: String = "",
    val attendance: Int = 0,
    val absence: Int = 0,
    val late: Int = 0,
    val tagIds: List<String> = emptyList(),
    val code: String? = null,
)

data class CreateRegisteredCourseInput(
    val year: Int,
    val name: String,
    val instructors: String,
    val credit: String,
    val methods: List<CourseMethod>,
    val schedules: List<CourseSchedule>,
)

data class UpdateRegisteredCourseInput(
    val id: String,
    val name: String? = null,
    val instructors: String? = null,
    val credit: String? = null,
    val methods: List<CourseMethod>? = null,
    val schedules: List<CourseSchedule>? = null,
    val memo: String? = null,
    val attendance: Int? = null,
    val absence: Int? = null,
    val late: Int? = null,
    val tagIds: List<String>? = null,
)

data class DailyCourse(
    val id: String,
    val year: Int,
    val name: String,
    val instructors: String,
    val credit: String,
    val methods: List<CourseMethod>,
    val schedules: List<CourseSchedule>,
    val memo: String = "",
    val attendance: Int = 0,
    val absence: Int = 0,
    val late: Int = 0,
    val code: String? = null,
)

fun RegisteredCourse.toDailyCourse(): DailyCourse =
    DailyCourse(
        id = id,
        year = year,
        name = name,
        instructors = instructors,
        credit = credit,
        methods = methods,
        schedules = schedules,
        memo = memo,
        attendance = attendance,
        absence = absence,
        late = late,
        code = code,
    )

data class SchoolCalendarEvent(
    val id: Int,
    val type: EventType,
    val date: LocalDate,
    val description: String,
    val changeTo: Day? = null,
)

data class Tag(
    val id: String,
    val name: String,
    val order: Int,
)

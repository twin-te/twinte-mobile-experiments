package net.twinte.mobile_experiments.core.domain

import kotlinx.datetime.LocalDate

enum class Module(val label: String) {
    Unknown(""),
    SpringA("春A"),
    SpringB("春B"),
    SpringC("春C"),
    SummerVacation("夏休み"),
    FallA("秋A"),
    FallB("秋B"),
    FallC("秋C"),
    SpringVacation("春休み"),
}

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

data class CourseSchedule(
    val module: Module,
    val day: Day,
    val period: Int,
    val locations: String,
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

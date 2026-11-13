package net.twinte.mobile_experiments

import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

internal enum class AttendanceCounter {
    Attendance, Absence, Late;

    fun value(course: RegisteredCourse): Int = when (this) {
        Attendance -> course.attendance
        Absence -> course.absence
        Late -> course.late
    }

    fun updateInput(courseId: String, value: Int): UpdateRegisteredCourseInput = when (this) {
        Attendance -> UpdateRegisteredCourseInput(courseId, attendance = value)
        Absence -> UpdateRegisteredCourseInput(courseId, absence = value)
        Late -> UpdateRegisteredCourseInput(courseId, late = value)
    }
}

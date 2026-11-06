package net.twinte.mobile_experiments

import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.feature.course.courseScheduleText
import net.twinte.mobile_experiments.feature.course.detailLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseDetailPresentationTest {
    @Test
    fun schedulesGroupTheSameSlotWithoutDroppingModulesOrSpecialDays() {
        val schedules = listOf(
            CourseSchedule(Module.FallB, Day.Monday, 2, "3A204"),
            CourseSchedule(Module.SpringA, Day.Monday, 2, "1A101"),
            CourseSchedule(Module.SpringA, Day.Monday, 2, "3A204"),
            CourseSchedule(Module.SummerVacation, Day.Intensive, 0, ""),
            CourseSchedule(Module.Unknown, Day.Nt, 0, ""),
        )
        assertEquals("春A・秋B 月曜2限\n夏休み 集中\nNT", courseScheduleText(schedules))
        assertEquals("", courseScheduleText(emptyList()))
    }

    @Test
    fun methodsUseTheWebDisplayLabels() {
        assertEquals(
            listOf("オンデマンド", "同時双方向", "対面", "その他"),
            CourseMethod.entries.map { it.detailLabel },
        )
    }
}

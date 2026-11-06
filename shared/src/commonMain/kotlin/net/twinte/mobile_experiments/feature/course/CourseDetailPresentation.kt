package net.twinte.mobile_experiments.feature.course

import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule

internal fun courseScheduleText(schedules: List<CourseSchedule>): String =
    schedules.groupBy { it.day to it.period }.map { (slot, entries) ->
        val modules = entries.map { it.module }.distinct().sortedBy { it.ordinal }
            .map { it.label }.filter(String::isNotBlank).joinToString("・")
        listOf(
            modules,
            slot.first.label + if (slot.second > 0) "${slot.second}限" else "",
        ).filter(String::isNotBlank).joinToString(" ")
    }.joinToString("\n")

internal val CourseMethod.detailLabel: String
    get() = when (this) {
        CourseMethod.FaceToFace -> "対面"
        CourseMethod.OnlineSynchronous -> "同時双方向"
        CourseMethod.OnlineAsynchronous -> "オンデマンド"
        CourseMethod.Others -> "その他"
    }

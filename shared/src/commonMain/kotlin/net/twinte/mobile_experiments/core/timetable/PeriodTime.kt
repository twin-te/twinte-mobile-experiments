package net.twinte.mobile_experiments.core.timetable

data class SimpleTime(
    val hour: Int,
    val minute: Int,
) {
    override fun toString(): String = "$hour:${minute.toString().padStart(2, '0')}"
}

data class PeriodTime(
    val period: Int,
    val start: SimpleTime,
    val end: SimpleTime,
)

object PeriodTimes {
    val regular: List<PeriodTime> = listOf(
        PeriodTime(1, SimpleTime(8, 40), SimpleTime(9, 55)),
        PeriodTime(2, SimpleTime(10, 10), SimpleTime(11, 25)),
        PeriodTime(3, SimpleTime(12, 15), SimpleTime(13, 30)),
        PeriodTime(4, SimpleTime(13, 45), SimpleTime(15, 0)),
        PeriodTime(5, SimpleTime(15, 15), SimpleTime(16, 30)),
        PeriodTime(6, SimpleTime(16, 45), SimpleTime(18, 0)),
    )

    fun startLabel(period: Int): String =
        regular.firstOrNull { it.period == period }?.start?.toString() ?: "-"

    fun endLabel(period: Int): String =
        regular.firstOrNull { it.period == period }?.end?.toString() ?: "-"
}

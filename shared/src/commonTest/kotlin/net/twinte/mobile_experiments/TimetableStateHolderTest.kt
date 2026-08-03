package net.twinte.mobile_experiments

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.core.timetable.TimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableStateHolderTest {
    @Test
    fun changingDateSwitchesDailyObservation() = runTest {
        val firstDate = LocalDate(2026, 5, 1)
        val secondDate = LocalDate(2026, 5, 2)
        val dailyService = FakeDailyTimetableService()
        val stateHolder = TimetableStateHolder(
            timetableService = FakeTimetableService(),
            dailyTimetableService = dailyService,
            initialDate = firstDate,
            scope = backgroundScope,
            onFailure = {},
        )

        stateHolder.bind("user-id", firstDate)
        runCurrent()
        assertEquals(firstDate, stateHolder.dailyTimetableUiState.value.date)

        stateHolder.bind("user-id", secondDate)
        runCurrent()

        assertEquals(secondDate, stateHolder.dailyTimetableUiState.value.date)
        assertEquals(listOf(firstDate, secondDate), dailyService.observedDates)
    }
}

private class FakeTimetableService : TimetableService {
    override fun observeTimetable(accountId: String, year: Int?): Flow<TimetableSnapshot> =
        MutableStateFlow(TimetableSnapshot())

    override suspend fun refresh(accountId: String, year: Int?) = Unit
}

private class FakeDailyTimetableService : DailyTimetableService {
    val observedDates = mutableListOf<LocalDate>()

    override fun observe(accountId: String, date: LocalDate): Flow<DailyTimetableSnapshot?> {
        observedDates += date
        return MutableStateFlow(null)
    }

    override suspend fun refresh(accountId: String, date: LocalDate) = Unit
}

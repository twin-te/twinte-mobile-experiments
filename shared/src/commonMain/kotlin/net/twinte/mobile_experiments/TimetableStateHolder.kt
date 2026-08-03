package net.twinte.mobile_experiments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.timetable.DailyCourseSlot
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableService
import kotlin.time.Instant

internal data class TimetableUiState(
    val message: String = "No saved timetable",
    val isLoading: Boolean = false,
    val courseCount: Int? = null,
    val tagCount: Int? = null,
    val firstCourseName: String? = null,
    val lastSuccessfulSyncAt: Instant? = null,
)

internal data class DailyTimetableUiState(
    val message: String = "No saved daily schedule",
    val isLoading: Boolean = false,
    val date: LocalDate,
    val moduleLabel: String? = null,
    val eventLabel: String? = null,
    val lectureCount: Int? = null,
    val firstCourseName: String? = null,
    val lastSuccessfulSyncAt: Instant? = null,
)

internal class TimetableStateHolder(
    private val timetableService: TimetableService,
    private val dailyTimetableService: DailyTimetableService,
    initialDate: LocalDate,
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit,
) {
    private val _timetableUiState = MutableStateFlow(TimetableUiState())
    val timetableUiState: StateFlow<TimetableUiState> = _timetableUiState.asStateFlow()

    private val _dailyTimetableUiState = MutableStateFlow(DailyTimetableUiState(date = initialDate))
    val dailyTimetableUiState: StateFlow<DailyTimetableUiState> = _dailyTimetableUiState.asStateFlow()

    private var accountId: String? = null
    private var date: LocalDate = initialDate
    private var timetableObservation: Job? = null
    private var dailyObservation: Job? = null
    private var timetableRefresh: Job? = null
    private var dailyRefresh: Job? = null

    fun bind(accountId: String?, date: LocalDate) {
        val accountChanged = this.accountId != accountId
        val dateChanged = this.date != date
        this.accountId = accountId
        this.date = date

        if (accountChanged) {
            timetableObservation?.cancel()
            timetableRefresh?.cancel()
            _timetableUiState.value = TimetableUiState()
            timetableObservation = accountId?.let(::observeTimetable)
        }
        if (accountChanged || dateChanged) {
            dailyObservation?.cancel()
            dailyRefresh?.cancel()
            _dailyTimetableUiState.value = DailyTimetableUiState(date = date)
            dailyObservation = accountId?.let { observeDailyTimetable(it, date) }
        }
    }

    fun refreshTimetable() {
        if (_timetableUiState.value.isLoading) return
        val accountId = accountId ?: return
        _timetableUiState.update { it.copy(isLoading = true, message = "Refreshing timetable...") }
        timetableRefresh = scope.launch {
            val succeeded = runRefresh(
                operation = { timetableService.refresh(accountId) },
                onError = {
                    _timetableUiState.update {
                        it.copy(message = "Refresh failed; showing saved timetable", isLoading = false)
                    }
                },
            )
            if (succeeded) {
                _timetableUiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshDailyTimetable() {
        if (_dailyTimetableUiState.value.isLoading) return
        val accountId = accountId ?: return
        val date = date
        _dailyTimetableUiState.update {
            it.copy(isLoading = true, message = "Refreshing today's schedule...")
        }
        dailyRefresh = scope.launch {
            val succeeded = runRefresh(
                operation = { dailyTimetableService.refresh(accountId, date) },
                onError = {
                    _dailyTimetableUiState.update {
                        it.copy(message = "Refresh failed; showing saved daily schedule", isLoading = false)
                    }
                },
            )
            if (succeeded) {
                _dailyTimetableUiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun observeTimetable(accountId: String): Job =
        scope.launch {
            timetableService.observeTimetable(accountId).collect { snapshot ->
                _timetableUiState.value = TimetableUiState(
                    message = if (snapshot.lastSuccessfulSyncAt == null) {
                        "No saved timetable"
                    } else {
                        "Showing saved timetable"
                    },
                    courseCount = snapshot.courses.size,
                    tagCount = snapshot.tags.size,
                    firstCourseName = snapshot.courses.firstOrNull()?.name,
                    lastSuccessfulSyncAt = snapshot.lastSuccessfulSyncAt,
                )
            }
        }

    private fun observeDailyTimetable(accountId: String, date: LocalDate): Job =
        scope.launch {
            dailyTimetableService.observe(accountId, date).collect { snapshot ->
                _dailyTimetableUiState.value = if (snapshot == null) {
                    DailyTimetableUiState(date = date)
                } else {
                    val timetable = snapshot.timetable
                    DailyTimetableUiState(
                        message = "Showing saved daily schedule",
                        date = timetable.date,
                        moduleLabel = timetable.module.label,
                        eventLabel = timetable.eventLabel,
                        lectureCount = timetable.lectureCount,
                        firstCourseName = timetable.slots
                            .filterIsInstance<DailyCourseSlot.Course>()
                            .firstOrNull()
                            ?.name,
                        lastSuccessfulSyncAt = snapshot.lastSuccessfulSyncAt,
                    )
                }
            }
        }

    private suspend fun runRefresh(
        operation: suspend () -> Unit,
        onError: () -> Unit,
    ): Boolean =
        try {
            operation()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onFailure(error)
            onError()
            false
        }
}

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
import kotlinx.datetime.Month
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import net.twinte.mobile_experiments.core.timetable.DailyCourseSlot
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import kotlin.time.Instant

internal data class TimetableUiState(
    val selectedYear: Int,
    val selectedModule: Module = Module.SpringA,
    val message: String = "No saved timetable",
    val isLoading: Boolean = false,
    val courseCount: Int? = null,
    val tagCount: Int? = null,
    val cells: Map<TimetableSlot, List<TimetableCourseSummary>> = emptyMap(),
    val registeredCourses: List<RegisteredCourse> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val lastSuccessfulSyncAt: Instant? = null,
)

internal data class CourseOperationsUiState(
    val searchResults: List<Course> = emptyList(),
    val message: String? = null,
    val isLoading: Boolean = false,
)

internal data class TimetableSlot(
    val day: Day,
    val period: Int,
)

internal data class TimetableCourseSummary(
    val name: String,
    val locations: String,
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
    private val initialYear = initialDate.academicYear
    private val _timetableUiState = MutableStateFlow(TimetableUiState(selectedYear = initialYear))
    val timetableUiState: StateFlow<TimetableUiState> = _timetableUiState.asStateFlow()

    private val _courseOperationsUiState = MutableStateFlow(CourseOperationsUiState())
    val courseOperationsUiState: StateFlow<CourseOperationsUiState> = _courseOperationsUiState.asStateFlow()

    private val _dailyTimetableUiState = MutableStateFlow(DailyTimetableUiState(date = initialDate))
    val dailyTimetableUiState: StateFlow<DailyTimetableUiState> = _dailyTimetableUiState.asStateFlow()

    private var accountId: String? = null
    private var date: LocalDate = initialDate
    private var timetableObservation: Job? = null
    private var dailyObservation: Job? = null
    private var timetableRefresh: Job? = null
    private var dailyRefresh: Job? = null
    private var courseOperation: Job? = null
    private var timetableSnapshot = TimetableSnapshot()

    fun bind(accountId: String?, date: LocalDate) {
        val accountChanged = this.accountId != accountId
        val dateChanged = this.date != date
        this.accountId = accountId
        this.date = date

        if (accountChanged) {
            timetableObservation?.cancel()
            timetableRefresh?.cancel()
            courseOperation?.cancel()
            timetableSnapshot = TimetableSnapshot()
            _timetableUiState.value = TimetableUiState(
                selectedYear = _timetableUiState.value.selectedYear,
                selectedModule = _timetableUiState.value.selectedModule,
            )
            _courseOperationsUiState.value = CourseOperationsUiState()
            timetableObservation = accountId?.let(::observeTimetable)
        }
        if (accountChanged || dateChanged) {
            dailyObservation?.cancel()
            dailyRefresh?.cancel()
            _dailyTimetableUiState.value = DailyTimetableUiState(date = date)
            dailyObservation = accountId?.let { observeDailyTimetable(it, date) }
        }
    }

    fun selectPreviousYear() {
        selectYear(_timetableUiState.value.selectedYear - 1)
    }

    fun selectNextYear() {
        selectYear(_timetableUiState.value.selectedYear + 1)
    }

    fun selectModule(module: Module) {
        val current = _timetableUiState.value
        if (module == current.selectedModule) return
        _timetableUiState.value = timetableSnapshot.toUiState(
            selectedYear = current.selectedYear,
            selectedModule = module,
            isLoading = current.isLoading,
        )
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

    fun searchCourses(query: String) {
        val year = _timetableUiState.value.selectedYear
        launchCourseOperation("Searching courses...") {
            val keywords = query.trim()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
            val results = timetableService.searchCourses(
                SearchCoursesInput(
                    year = year,
                    keywords = keywords,
                ),
            )
            _courseOperationsUiState.value = CourseOperationsUiState(
                searchResults = results,
                message = if (results.isEmpty()) "No courses found" else "${results.size} courses found",
            )
        }
    }

    fun registerCourse(course: Course) {
        val accountId = accountId ?: return
        launchCourseOperation("Registering ${course.name}...") {
            timetableService.createCoursesByCodes(accountId, course.year, listOf(course.code))
            _courseOperationsUiState.update {
                it.copy(message = "Registered ${course.name}", isLoading = false)
            }
        }
    }

    fun updateCourse(input: UpdateRegisteredCourseInput) {
        val accountId = accountId ?: return
        launchCourseOperation("Updating course...") {
            val course = timetableService.updateCourse(accountId, input)
            _courseOperationsUiState.update {
                it.copy(message = "Updated ${course.name}", isLoading = false)
            }
        }
    }

    fun deleteCourse(course: RegisteredCourse) {
        val accountId = accountId ?: return
        launchCourseOperation("Deleting ${course.name}...") {
            timetableService.deleteCourse(accountId, course.id)
            _courseOperationsUiState.update {
                it.copy(message = "Deleted ${course.name}", isLoading = false)
            }
        }
    }

    fun createTag(name: String) {
        val accountId = accountId ?: return
        launchCourseOperation("Creating tag...") {
            val tag = timetableService.createTag(accountId, name)
            _courseOperationsUiState.update {
                it.copy(message = "Created ${tag.name}", isLoading = false)
            }
        }
    }

    fun updateTag(tag: Tag, name: String) {
        val accountId = accountId ?: return
        launchCourseOperation("Updating tag...") {
            val updatedTag = timetableService.updateTag(accountId, tag.id, name)
            _courseOperationsUiState.update {
                it.copy(message = "Updated ${updatedTag.name}", isLoading = false)
            }
        }
    }

    fun deleteTag(tag: Tag) {
        val accountId = accountId ?: return
        launchCourseOperation("Deleting ${tag.name}...") {
            timetableService.deleteTag(accountId, tag.id)
            _courseOperationsUiState.update {
                it.copy(message = "Deleted ${tag.name}", isLoading = false)
            }
        }
    }

    fun moveTag(tag: Tag, offset: Int) {
        val accountId = accountId ?: return
        val tags = timetableSnapshot.tags.sortedBy(Tag::order).toMutableList()
        val currentIndex = tags.indexOfFirst { it.id == tag.id }
        val destinationIndex = currentIndex + offset
        if (currentIndex == -1 || destinationIndex !in tags.indices) return
        val movedTag = tags.removeAt(currentIndex)
        tags.add(destinationIndex, movedTag)

        launchCourseOperation("Rearranging tags...") {
            timetableService.rearrangeTags(accountId, tags.map(Tag::id))
            _courseOperationsUiState.update {
                it.copy(message = "Rearranged tags", isLoading = false)
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
                timetableSnapshot = snapshot
                val current = _timetableUiState.value
                _timetableUiState.value = snapshot.toUiState(
                    selectedYear = current.selectedYear,
                    selectedModule = current.selectedModule,
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

    private fun launchCourseOperation(
        loadingMessage: String,
        operation: suspend () -> Unit,
    ) {
        if (_courseOperationsUiState.value.isLoading) return
        _courseOperationsUiState.update { it.copy(message = loadingMessage, isLoading = true) }
        courseOperation = scope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onFailure(error)
                _courseOperationsUiState.update {
                    it.copy(message = "Operation failed", isLoading = false)
                }
            }
        }
    }

    private fun selectYear(year: Int) {
        val current = _timetableUiState.value
        _courseOperationsUiState.value = CourseOperationsUiState()
        _timetableUiState.value = timetableSnapshot.toUiState(
            selectedYear = year,
            selectedModule = current.selectedModule,
            isLoading = current.isLoading,
        )
    }
}

private val LocalDate.academicYear: Int
    get() = if (month >= Month.APRIL) year else year - 1

private fun TimetableSnapshot.toUiState(
    selectedYear: Int,
    selectedModule: Module,
    isLoading: Boolean = false,
): TimetableUiState {
    val selectedCourses = courses.filter { it.year == selectedYear }
    val cells = selectedCourses
        .flatMap { course ->
            course.schedules
                .filter { it.module == selectedModule }
                .map { schedule ->
                    TimetableSlot(schedule.day, schedule.period) to TimetableCourseSummary(
                        name = course.name,
                        locations = schedule.locations,
                    )
                }
        }
        .groupBy({ it.first }, { it.second })
    return TimetableUiState(
        selectedYear = selectedYear,
        selectedModule = selectedModule,
        message = if (lastSuccessfulSyncAt == null) "No saved timetable" else "Showing saved timetable",
        isLoading = isLoading,
        courseCount = selectedCourses.size,
        tagCount = tags.size,
        cells = cells,
        registeredCourses = selectedCourses,
        tags = tags.sortedBy(Tag::order),
        lastSuccessfulSyncAt = lastSuccessfulSyncAt,
    )
}

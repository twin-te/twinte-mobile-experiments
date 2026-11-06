package net.twinte.mobile_experiments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
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
import net.twinte.mobile_experiments.feature.timetable.calendarModuleToTimetableModule
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val automaticSyncInterval = 5.minutes

internal data class TimetableUiState(
    val selectedYear: Int,
    val selectedModule: Module = Module.SpringA,
    val isLoading: Boolean = false,
    val cells: Map<TimetableSlot, List<TimetableCourseSummary>> = emptyMap(),
    val registeredCourses: List<RegisteredCourse> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val lastSuccessfulSyncAt: Instant? = null,
    val refreshFailed: Boolean = false,
)

internal data class CourseOperationsUiState(
    val searchResults: List<Course> = emptyList(),
    val message: String? = null,
    val isLoading: Boolean = false,
    val operationFailed: Boolean = false,
)

internal data class TimetableSlot(
    val day: Day,
    val period: Int,
)

internal data class TimetableCourseSummary(
    val name: String,
    val locations: String,
    val id: String,
)

internal data class DailyTimetableUiState(
    val message: String = "No saved daily schedule",
    val isLoading: Boolean = false,
    val date: LocalDate,
    val module: Module? = null,
    val eventLabel: String? = null,
    val lectureCount: Int? = null,
    val firstCourseName: String? = null,
    val lastSuccessfulSyncAt: Instant? = null,
) {
    val moduleLabel: String? get() = module?.label
    val currentModule: Module? get() = calendarModuleToTimetableModule(module, date)
}

internal class TimetableStateHolder(
    private val timetableService: TimetableService,
    private val dailyTimetableService: DailyTimetableService,
    initialDate: LocalDate,
    private val scope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit,
    private val now: () -> Instant = Clock.System::now,
    initialYear: Int = initialDate.academicYear,
    initialModule: Module? = null,
) {
    private val _timetableUiState = MutableStateFlow(
        TimetableUiState(selectedYear = initialYear, selectedModule = initialModule ?: Module.SpringA),
    )
    val timetableUiState: StateFlow<TimetableUiState> = _timetableUiState.asStateFlow()
    private var moduleInitialized = initialModule != null
    // Keep initialization pending across restoration if the calendar has not arrived yet.
    val moduleForRestoration: Module?
        get() = _timetableUiState.value.selectedModule.takeIf { moduleInitialized }

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
    private var courseSearch: Job? = null
    private var courseOperation: Job? = null
    private var timetableSnapshot = TimetableSnapshot()
    private var timetableCacheObserved = false
    private var dailyCacheObserved = false
    private var timetableSyncRequested = false
    private var dailySyncRequested = false

    fun bind(accountId: String?, date: LocalDate) {
        val accountChanged = this.accountId != accountId
        val dateChanged = this.date != date
        this.accountId = accountId
        this.date = date

        if (accountChanged) {
            timetableObservation?.cancel()
            timetableRefresh?.cancel()
            courseSearch?.cancel()
            courseOperation?.cancel()
            timetableSnapshot = TimetableSnapshot()
            timetableCacheObserved = false
            timetableSyncRequested = false
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
            dailyCacheObserved = false
            dailySyncRequested = false
            _dailyTimetableUiState.value = DailyTimetableUiState(date = date)
            dailyObservation = accountId?.let { observeDailyTimetable(it, date) }
        }
    }

    fun syncIfStale() {
        if (accountId == null) return
        timetableSyncRequested = true
        dailySyncRequested = true
        syncTimetableIfReady()
        syncDailyIfReady()
    }

    fun selectPreviousYear() {
        selectYear(_timetableUiState.value.selectedYear - 1)
    }

    fun selectNextYear() {
        selectYear(_timetableUiState.value.selectedYear + 1)
    }

    fun selectCurrentModule(module: Module) {
        val currentModule = calendarModuleToTimetableModule(module, date) ?: return
        if (_timetableUiState.value.selectedYear != date.academicYear) selectYear(date.academicYear)
        selectModule(currentModule)
    }

    fun selectModule(module: Module) {
        moduleInitialized = true
        val current = _timetableUiState.value
        if (module == current.selectedModule) return
        _timetableUiState.value = timetableSnapshot.toUiState(
            selectedYear = current.selectedYear,
            selectedModule = module,
            isLoading = current.isLoading,
            refreshFailed = current.refreshFailed,
        )
    }

    fun refreshTimetable() {
        if (timetableRefresh?.isActive == true) return
        val accountId = accountId ?: return
        _timetableUiState.update {
            it.copy(isLoading = true, refreshFailed = false)
        }
        timetableRefresh = scope.launch {
            val succeeded = runRefresh(
                operation = { timetableService.refresh(accountId) },
                onError = {
                    _timetableUiState.update {
                        it.copy(isLoading = false, refreshFailed = true)
                    }
                },
            )
            if (succeeded) {
                _timetableUiState.update { it.copy(isLoading = false, refreshFailed = false) }
            }
        }
    }

    fun searchCourses(query: String) {
        if (courseSearch?.isActive == true || courseOperation?.isActive == true) return
        val year = _timetableUiState.value.selectedYear
        _courseOperationsUiState.update { it.copy(message = "Searching courses...", isLoading = true) }
        courseSearch = scope.launch {
            try {
                val keywords = query.trim()
                    .split(Regex("\\s+"))
                    .filter(String::isNotBlank)
                val results = timetableService.searchCourses(
                    SearchCoursesInput(year = year, keywords = keywords),
                )
                _courseOperationsUiState.value = CourseOperationsUiState(
                    searchResults = results,
                    message = if (results.isEmpty()) "No courses found" else "${results.size} courses found",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onFailure(error)
                _courseOperationsUiState.update {
                    it.copy(message = "Search failed", isLoading = false)
                }
            }
        }
    }

    fun registerCourse(course: Course) {
        val accountId = accountId ?: return
        launchCourseOperation("Registering ${course.name}...") {
            timetableService.createCoursesByCodes(accountId, course.year, listOf(course.code))
            completeCourseMutation(accountId, "Registered ${course.name}")
        }
    }

    fun createCourseManually(input: CreateRegisteredCourseInput, onSuccess: () -> Unit = {}) {
        val accountId = accountId ?: return
        launchCourseOperation("Creating course...") {
            val course = timetableService.createCourseManually(accountId, input)
            currentCoroutineContext().ensureActive()
            onSuccess()
            completeCourseMutation(accountId, "Created ${course.name}")
        }
    }

    fun updateCourse(input: UpdateRegisteredCourseInput, onSuccess: () -> Unit = {}) {
        val accountId = accountId ?: return
        launchCourseOperation("Updating course...") {
            val course = timetableService.updateCourse(accountId, input)
            currentCoroutineContext().ensureActive()
            onSuccess()
            completeCourseMutation(accountId, "Updated ${course.name}")
        }
    }

    fun changeCourseAttendance(courseId: String, counter: AttendanceCounter, delta: Int) {
        if (accountId == null || (delta != -1 && delta != 1)) return
        val course = timetableSnapshot.courses.firstOrNull { it.id == courseId } ?: return
        val newValue = counter.value(course).toLong() + delta
        if (newValue !in 0..Int.MAX_VALUE.toLong()) return
        updateCourse(counter.updateInput(courseId, newValue.toInt()))
    }

    fun deleteCourse(course: RegisteredCourse) {
        val accountId = accountId ?: return
        launchCourseOperation("Deleting ${course.name}...") {
            timetableService.deleteCourse(accountId, course.id)
            completeCourseMutation(accountId, "Deleted ${course.name}")
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
        if (dailyRefresh?.isActive == true) return
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
                if (this@TimetableStateHolder.accountId != accountId) return@collect
                timetableSnapshot = snapshot
                val current = _timetableUiState.value
                _timetableUiState.value = snapshot.toUiState(
                    selectedYear = current.selectedYear,
                    selectedModule = current.selectedModule,
                    isLoading = current.isLoading,
                    refreshFailed = current.refreshFailed,
                )
                timetableCacheObserved = true
                syncTimetableIfReady()
            }
        }

    private fun observeDailyTimetable(accountId: String, date: LocalDate): Job =
        scope.launch {
            dailyTimetableService.observe(accountId, date).collect { snapshot ->
                if (this@TimetableStateHolder.accountId != accountId || this@TimetableStateHolder.date != date) {
                    return@collect
                }
                val current = _dailyTimetableUiState.value
                val updated = if (snapshot == null) {
                    DailyTimetableUiState(date = date)
                } else {
                    val timetable = snapshot.timetable
                    DailyTimetableUiState(
                        message = "Showing saved daily schedule",
                        date = timetable.date,
                        module = timetable.module,
                        eventLabel = timetable.eventLabel,
                        lectureCount = timetable.lectureCount,
                        firstCourseName = timetable.slots
                            .filterIsInstance<DailyCourseSlot.Course>()
                            .firstOrNull()
                            ?.name,
                        lastSuccessfulSyncAt = snapshot.lastSuccessfulSyncAt,
                    )
                }
                _dailyTimetableUiState.value = updated.copy(isLoading = current.isLoading)
                if (!moduleInitialized) updated.currentModule?.let(::selectModule)
                dailyCacheObserved = true
                syncDailyIfReady()
            }
        }

    private fun syncTimetableIfReady() {
        if (!timetableSyncRequested || !timetableCacheObserved) return
        timetableSyncRequested = false
        if (needsSync(_timetableUiState.value.lastSuccessfulSyncAt)) refreshTimetable()
    }

    private fun syncDailyIfReady() {
        if (!dailySyncRequested || !dailyCacheObserved) return
        dailySyncRequested = false
        if (needsSync(_dailyTimetableUiState.value.lastSuccessfulSyncAt)) refreshDailyTimetable()
    }

    private fun needsSync(lastSuccessfulSyncAt: Instant?): Boolean =
        lastSuccessfulSyncAt == null || now() - lastSuccessfulSyncAt >= automaticSyncInterval

    private suspend fun runRefresh(
        operation: suspend () -> Unit,
        onError: () -> Unit,
    ): Boolean =
        try {
            operation()
            currentCoroutineContext().ensureActive()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            onFailure(error)
            onError()
            false
        }

    private suspend fun completeCourseMutation(accountId: String, successMessage: String) {
        val date = date
        val dailyRefreshSucceeded = try {
            dailyTimetableService.refresh(accountId, date)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error is TwinteApiException && error.isUnauthorized) {
                onFailure(error)
            }
            if (this.date == date) {
                _dailyTimetableUiState.update {
                    it.copy(message = "Refresh failed; showing saved daily schedule")
                }
            }
            false
        }
        _courseOperationsUiState.update {
            it.copy(
                message = if (dailyRefreshSucceeded) {
                    successMessage
                } else {
                    "$successMessage; daily schedule refresh failed"
                },
                isLoading = false,
            )
        }
    }

    private fun launchCourseOperation(
        loadingMessage: String,
        operation: suspend () -> Unit,
    ) {
        if (courseSearch?.isActive == true || courseOperation?.isActive == true) return
        _courseOperationsUiState.update {
            it.copy(message = loadingMessage, isLoading = true, operationFailed = false)
        }
        courseOperation = scope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onFailure(error)
                _courseOperationsUiState.update {
                    it.copy(message = "Operation failed", isLoading = false, operationFailed = true)
                }
            }
        }
    }

    private fun selectYear(year: Int) {
        val current = _timetableUiState.value
        courseSearch?.cancel()
        _courseOperationsUiState.update {
            if (courseOperation?.isActive == true) {
                it.copy(searchResults = emptyList())
            } else {
                CourseOperationsUiState()
            }
        }
        _timetableUiState.value = timetableSnapshot.toUiState(
            selectedYear = year,
            selectedModule = current.selectedModule,
            isLoading = current.isLoading,
            refreshFailed = current.refreshFailed,
        )
    }
}

@Composable
internal fun rememberTimetableStateHolder(
    timetableService: TimetableService,
    dailyTimetableService: DailyTimetableService,
    initialDate: LocalDate,
    scope: CoroutineScope,
    onFailure: (Throwable) -> Unit,
): TimetableStateHolder {
    fun create(year: Int, module: Module?) = TimetableStateHolder(
        timetableService = timetableService,
        dailyTimetableService = dailyTimetableService,
        initialDate = initialDate,
        scope = scope,
        onFailure = onFailure,
        initialYear = year,
        initialModule = module,
    )

    val saver = listSaver<TimetableStateHolder, String>(
        save = {
            val state = it.timetableUiState.value
            listOf(state.selectedYear.toString(), it.moduleForRestoration?.name.orEmpty())
        },
        restore = { create(it[0].toInt(), it[1].takeIf(String::isNotEmpty)?.let(Module::valueOf)) },
    )
    return rememberSaveable(timetableService, dailyTimetableService, scope, onFailure, saver = saver) {
        create(initialDate.academicYear, null)
    }
}

internal val LocalDate.academicYear: Int
    get() = if (month >= Month.APRIL) year else year - 1

private fun TimetableSnapshot.toUiState(
    selectedYear: Int,
    selectedModule: Module,
    isLoading: Boolean = false,
    refreshFailed: Boolean = false,
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
                        id = course.id,
                    )
                }
        }
        .groupBy({ it.first }, { it.second })
    return TimetableUiState(
        selectedYear = selectedYear,
        selectedModule = selectedModule,
        isLoading = isLoading,
        cells = cells,
        registeredCourses = selectedCourses,
        tags = tags.sortedBy(Tag::order),
        lastSuccessfulSyncAt = lastSuccessfulSyncAt,
        refreshFailed = refreshFailed,
    )
}

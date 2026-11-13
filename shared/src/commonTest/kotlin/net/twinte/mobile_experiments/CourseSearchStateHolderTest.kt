package net.twinte.mobile_experiments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CourseSearchStateHolderTest {
    private val date = LocalDate(2026, 11, 8)

    @Test
    fun paginationRetriesTheSamePageAndRetainsResultsOnFailure() = runTest {
        var fail = true
        val service = FakeTimetableService(searchResultProvider = { input ->
            if (input.offset == 2 && fail) error("offline")
            if (input.offset == 0) listOf(searchTestCourse("A"), searchTestCourse("B"))
            else listOf(searchTestCourse("B"), searchTestCourse("C"))
        })
        val holder = TimetableStateHolder(service, FakeDailyTimetableService(), date, backgroundScope, {})
        holder.bind("user", date)
        runCurrent()
        holder.searchCourses(SearchCoursesInput(2026, keywords = listOf("original"), limit = 2))
        runCurrent()
        assertTrue(holder.courseOperationsUiState.value.canLoadMore)
        holder.loadMoreCourses()
        runCurrent()
        assertTrue(holder.courseOperationsUiState.value.searchFailed)
        assertEquals(listOf("A", "B"), holder.courseOperationsUiState.value.searchResults.map { it.code })
        fail = false
        holder.retryCourseSearch()
        runCurrent()
        assertEquals(listOf("A", "B", "C"), holder.courseOperationsUiState.value.searchResults.map { it.code })
        assertEquals(listOf(0, 2, 2), service.searchInputs.map { it.offset })
        assertTrue(service.searchInputs.all { it.keywords == listOf("original") })
        assertFalse(holder.courseOperationsUiState.value.searchFailed)
    }

    @Test
    fun changingTheYearDropsPaginationAndItsRetryRequest() = runTest {
        val service = FakeTimetableService(searchResultProvider = { error("offline") })
        val holder = TimetableStateHolder(service, FakeDailyTimetableService(), date, backgroundScope, {})
        holder.bind("user", date)
        holder.searchCourses(SearchCoursesInput(2026))
        runCurrent()
        holder.selectPreviousYear()
        holder.retryCourseSearch()
        holder.loadMoreCourses()
        runCurrent()
        assertEquals(1, service.searchInputs.size)
        assertEquals(CourseOperationsUiState(), holder.courseOperationsUiState.value)
    }

    @Test
    fun selectedCoursesAreRegisteredInOneRequestAndTheCallbackRunsOnlyAfterSuccess() = runTest {
        val pending = CompletableDeferred<Unit>()
        var fail = true
        val service = FakeTimetableService(registerOperation = {
            pending.await()
            if (fail) error("offline")
        })
        val holder = TimetableStateHolder(service, FakeDailyTimetableService(), date, backgroundScope, {})
        var successes = 0
        holder.bind("user", date)
        runCurrent()
        holder.registerCourses(2026, listOf("A", "B", "A")) { successes++ }
        holder.registerCourses(2026, listOf("C")) { successes++ }
        runCurrent()
        assertEquals(listOf(2026 to listOf("A", "B")), service.registrationRequests)
        assertEquals(0, successes)
        pending.complete(Unit)
        runCurrent()
        assertTrue(holder.courseOperationsUiState.value.operationFailed)
        assertEquals(emptyList(), holder.timetableUiState.value.registeredCourses)
        fail = false
        holder.registerCourses(2026, listOf("A", "B")) { successes++ }
        runCurrent()
        assertEquals(1, successes)
        assertEquals(listOf("A", "B"), holder.timetableUiState.value.registeredCourses.map { it.code })
        assertFalse(holder.courseOperationsUiState.value.operationFailed)
    }
}

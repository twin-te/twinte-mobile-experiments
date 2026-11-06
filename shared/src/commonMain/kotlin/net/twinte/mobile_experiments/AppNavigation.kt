package net.twinte.mobile_experiments

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.twinte.mobile_experiments.feature.timetable.TimetableCourseDialog
import net.twinte.mobile_experiments.feature.timetable.TimetableScreen

@Composable
internal fun AppNavigation(
    authUiState: AuthUiState,
    authStateHolder: AuthStateHolder,
    timetableStateHolder: TimetableStateHolder,
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
) {
    val timetable by timetableStateHolder.timetableUiState.collectAsStateWithLifecycle()
    val operations by timetableStateHolder.courseOperationsUiState.collectAsStateWithLifecycle()
    val daily by timetableStateHolder.dailyTimetableUiState.collectAsStateWithLifecycle()
    val navigator = rememberAppNavigator()
    val scope = rememberCoroutineScope()
    val operationsState = operations.copy(isLoading = operations.isLoading || authUiState.isLoading)
    var selectedCourseIds by rememberSerializable(timetable.selectedYear) { mutableStateOf(emptyList<String>()) }
    var editorTarget by rememberSerializable(timetable.selectedYear) {
        mutableStateOf<CourseEditorTarget>(CourseEditorTarget.Closed)
    }
    val coursesScroll = rememberSaveable(timetable.selectedYear, saver = ScrollState.Saver) { ScrollState(0) }
    fun openEditor(target: CourseEditorTarget) {
        if (operationsState.isLoading) return
        selectedCourseIds = emptyList()
        editorTarget = target
    }
    val refresh = {
        timetableStateHolder.refreshTimetable()
        timetableStateHolder.refreshDailyTimetable()
    }

    AppScaffold(
        authUiState = authUiState, navigator = navigator,
        selectedYear = timetable.selectedYear, calendarDate = daily.date, calendarLabel = daily.eventLabel,
        onPreviousYear = timetableStateHolder::selectPreviousYear,
        onNextYear = timetableStateHolder::selectNextYear,
        onRefresh = refresh, isRefreshing = timetable.isLoading || daily.isLoading,
    ) { route ->
        when (route) {
            AppRoute.Timetable -> TimetableScreen(
                uiState = timetable.copy(isLoading = timetable.isLoading || daily.isLoading || authUiState.isLoading),
                onRefresh = refresh,
                currentModule = daily.currentModule,
                currentYear = daily.date.academicYear,
                onCurrentModule = { daily.currentModule?.let(timetableStateHolder::selectCurrentModule) },
                onSelectModule = timetableStateHolder::selectModule,
                onOpenCourses = { selectedCourseIds = it },
                onOpenEmptySlot = {
                    navigator.openCourseSearch()
                    scope.launch { coursesScroll.scrollTo(0) }
                },
            )
            AppRoute.Today -> TodayScreen(
                uiState = daily.copy(isLoading = daily.isLoading || authUiState.isLoading),
                onRefresh = timetableStateHolder::refreshDailyTimetable,
            )
            AppRoute.Courses -> ScreenColumn(scrollState = coursesScroll) {
                TimetableYearSelector(
                    year = timetable.selectedYear,
                    onPreviousYear = timetableStateHolder::selectPreviousYear,
                    onNextYear = timetableStateHolder::selectNextYear,
                )
                CourseOperationsSection(
                    selectedYear = timetable.selectedYear,
                    registeredCourses = timetable.registeredCourses,
                    tags = timetable.tags,
                    uiState = operationsState,
                    onSearch = timetableStateHolder::searchCourses,
                    onRegister = timetableStateHolder::registerCourse,
                    onCreateCourse = { openEditor(CourseEditorTarget.Create) },
                    onEditCourse = { openEditor(CourseEditorTarget.Edit(it)) },
                    onChangeAttendance = timetableStateHolder::changeCourseAttendance,
                    onDelete = timetableStateHolder::deleteCourse,
                    onManageTags = navigator::openTags,
                )
            }
            AppRoute.Account -> AccountScreen(
                uiState = authUiState,
                isGoogleConfigured = isGoogleConfigured,
                isAppleConfigured = isAppleConfigured,
                onGoogleSignIn = authStateHolder::signInWithGoogle,
                onAppleSignIn = authStateHolder::signInWithApple,
                onRefresh = authStateHolder::refreshMe,
                onUnlinkProvider = authStateHolder::deleteUserAuthentication,
                onSignOut = authStateHolder::signOut,
                onDeleteAccount = authStateHolder::deleteAccount,
            )
            AppRoute.Tags -> ScreenColumn {
                operationsState.message?.let { Text(it) }
                if (operationsState.isLoading) LinearProgressIndicator()
                TagManagementSection(
                    tags = timetable.tags,
                    enabled = !operationsState.isLoading,
                    onCreate = timetableStateHolder::createTag,
                    onUpdate = timetableStateHolder::updateTag,
                    onDelete = timetableStateHolder::deleteTag,
                    onMove = timetableStateHolder::moveTag,
                )
            }
        }
    }
    val selectedCourses = timetable.registeredCourses.filter { it.id in selectedCourseIds }
    TimetableCourseDialog(
        courses = selectedCourses,
        onSelect = { selectedCourseIds = listOf(it) },
        onEdit = { openEditor(CourseEditorTarget.Edit(it)) },
        onDismiss = { selectedCourseIds = emptyList() },
        canEdit = !operationsState.isLoading,
    )
    val target = editorTarget
    val course = (target as? CourseEditorTarget.Edit)?.let { editing ->
        timetable.registeredCourses.firstOrNull { it.id == editing.courseId }
    }
    if (target == CourseEditorTarget.Create || course != null) {
        CourseEditorDialog(
            course = course, year = timetable.selectedYear, tags = timetable.tags,
            isSaving = operationsState.isLoading, saveFailed = operationsState.operationFailed,
            onCreate = timetableStateHolder::createCourseManually,
            onUpdate = timetableStateHolder::updateCourse,
            onDismiss = { editorTarget = CourseEditorTarget.Closed },
        )
    }
}

@Serializable
private sealed interface CourseEditorTarget {
    @Serializable data object Closed : CourseEditorTarget
    @Serializable data object Create : CourseEditorTarget
    @Serializable data class Edit(val courseId: String) : CourseEditorTarget
}

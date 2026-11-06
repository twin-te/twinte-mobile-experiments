package net.twinte.mobile_experiments

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.twinte.mobile_experiments.feature.timetable.TimetableScreen
import net.twinte.mobile_experiments.feature.timetable.TimetableCourseDialog

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
    val operationsState = operations.copy(isLoading = operations.isLoading || authUiState.isLoading)
    var selectedCourseIds by rememberSerializable(timetable.selectedYear) { mutableStateOf(emptyList<String>()) }
    var editingCourseId by rememberSaveable(timetable.selectedYear) { mutableStateOf<String?>(null) }
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
            )
            AppRoute.Today -> TodayScreen(
                uiState = daily.copy(isLoading = daily.isLoading || authUiState.isLoading),
                onRefresh = timetableStateHolder::refreshDailyTimetable,
            )
            AppRoute.Courses -> ScreenColumn {
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
                    onCreateCourse = timetableStateHolder::createCourseManually,
                    onUpdate = timetableStateHolder::updateCourse,
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
        onEdit = {
            selectedCourseIds = emptyList()
            editingCourseId = it
        },
        onDismiss = { selectedCourseIds = emptyList() },
    )
    timetable.registeredCourses.firstOrNull { it.id == editingCourseId }?.let { course ->
        CourseEditorDialog(
            course = course, year = timetable.selectedYear, tags = timetable.tags,
            isSaving = operationsState.isLoading, saveFailed = operationsState.operationFailed,
            onCreate = timetableStateHolder::createCourseManually,
            onUpdate = timetableStateHolder::updateCourse,
            onDismiss = { editingCourseId = null },
        )
    }
}

package net.twinte.mobile_experiments

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    AppScaffold(authUiState, navigator) { route ->
        when (route) {
            AppRoute.Timetable -> TimetableScreen(
                uiState = timetable.copy(isLoading = timetable.isLoading || authUiState.isLoading),
                onRefresh = timetableStateHolder::refreshTimetable,
                onPreviousYear = timetableStateHolder::selectPreviousYear,
                onNextYear = timetableStateHolder::selectNextYear,
                onSelectModule = timetableStateHolder::selectModule,
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
}

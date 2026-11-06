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
import net.twinte.mobile_experiments.feature.course.CourseDeleteDialog
import net.twinte.mobile_experiments.feature.course.CourseDetailActions
import net.twinte.mobile_experiments.feature.course.CourseDetailScreen
import net.twinte.mobile_experiments.feature.timetable.TimetableCourseSelectionDialog
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
    var deletingCourseId by rememberSaveable(timetable.selectedYear) { mutableStateOf<String?>(null) }
    fun openCourseDetail(courseId: String) {
        selectedCourseIds = emptyList()
        navigator.openCourseDetail(courseId)
    }
    fun openEditor(target: CourseEditorTarget) {
        if (operationsState.isLoading) return
        selectedCourseIds = emptyList()
        editorTarget = target
    }
    val refresh = {
        timetableStateHolder.refreshTimetable()
        timetableStateHolder.refreshDailyTimetable()
    }
    val previousYear = {
        navigator.dismissCourseDetails()
        timetableStateHolder.selectPreviousYear()
    }
    val nextYear = {
        navigator.dismissCourseDetails()
        timetableStateHolder.selectNextYear()
    }

    AppScaffold(
        authUiState = authUiState, navigator = navigator,
        selectedYear = timetable.selectedYear, calendarDate = daily.date, calendarLabel = daily.eventLabel,
        onPreviousYear = previousYear,
        onNextYear = nextYear,
        onRefresh = refresh, isRefreshing = timetable.isLoading || daily.isLoading,
        detailActions = { route ->
            timetable.registeredCourses.firstOrNull { it.id == route.courseId }?.let { course ->
                CourseDetailActions(
                    courseId = course.id, enabled = !operationsState.isLoading,
                    onEdit = { openEditor(CourseEditorTarget.Edit(course.id)) },
                    onDelete = { deletingCourseId = course.id },
                )
            }
        },
    ) { route ->
        when (route) {
            AppRoute.Timetable -> TimetableScreen(
                uiState = timetable.copy(isLoading = timetable.isLoading || daily.isLoading || authUiState.isLoading),
                onRefresh = refresh,
                currentModule = daily.currentModule,
                currentYear = daily.date.academicYear,
                onCurrentModule = { daily.currentModule?.let(timetableStateHolder::selectCurrentModule) },
                onSelectModule = timetableStateHolder::selectModule,
                onOpenCourses = { ids ->
                    if (ids.size == 1) openCourseDetail(ids.single()) else selectedCourseIds = ids
                },
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
                    onPreviousYear = previousYear,
                    onNextYear = nextYear,
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
                    onDelete = { timetableStateHolder.deleteCourse(it) },
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
            is AppRoute.CourseDetail -> CourseDetailScreen(
                course = timetable.registeredCourses.firstOrNull { it.id == route.courseId },
                tags = timetable.tags, isCacheLoaded = timetable.isCacheLoaded,
                isWorking = operationsState.isLoading, operationFailed = operationsState.operationFailed,
                onChangeAttendance = { counter, delta ->
                    timetableStateHolder.changeCourseAttendance(route.courseId, counter, delta)
                },
                onToggleTag = { timetableStateHolder.toggleCourseTag(route.courseId, it) },
                onCreateTag = { name, onCreated -> timetableStateHolder.createTagForCourse(route.courseId, name, onCreated) },
                onBack = navigator::goBack,
            )
        }
    }
    val selectedCourses = timetable.registeredCourses.filter { it.id in selectedCourseIds }
    TimetableCourseSelectionDialog(
        courses = selectedCourses,
        onSelect = ::openCourseDetail,
        onDismiss = { selectedCourseIds = emptyList() },
    )
    timetable.registeredCourses.firstOrNull { it.id == deletingCourseId }?.let { course ->
        CourseDeleteDialog(
            course = course, isWorking = operationsState.isLoading, operationFailed = operationsState.operationFailed,
            onDelete = {
                timetableStateHolder.deleteCourse(course) {
                    deletingCourseId = null
                    navigator.dismissCourseDetails(course.id)
                }
            },
            onDismiss = { deletingCourseId = null },
        )
    }
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

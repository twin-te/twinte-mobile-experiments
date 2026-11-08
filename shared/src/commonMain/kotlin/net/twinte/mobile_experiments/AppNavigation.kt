package net.twinte.mobile_experiments

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import net.twinte.mobile_experiments.feature.course.CourseDeleteDialog
import net.twinte.mobile_experiments.feature.course.CourseDetailActions
import net.twinte.mobile_experiments.feature.course.CourseDetailScreen
import net.twinte.mobile_experiments.feature.course.CourseEditorScreen
import net.twinte.mobile_experiments.feature.course.RegisteredCoursesScreen
import net.twinte.mobile_experiments.feature.course.search.CourseSearchDraft
import net.twinte.mobile_experiments.feature.course.search.CourseSearchScreen
import net.twinte.mobile_experiments.feature.course.courseMapUrl
import net.twinte.mobile_experiments.ui.browser.BrowserPage
import net.twinte.mobile_experiments.ui.browser.BrowserSheet
import net.twinte.mobile_experiments.ui.browser.browserPageSaver
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
    var searchDraft by rememberSerializable(timetable.selectedYear) { mutableStateOf(CourseSearchDraft()) }
    val searchScroll = rememberSaveable(timetable.selectedYear, saver = LazyListState.Saver) { LazyListState() }
    var deletingCourseId by rememberSaveable(timetable.selectedYear) { mutableStateOf<String?>(null) }
    var browserPage by rememberSaveable(timetable.selectedYear, stateSaver = browserPageSaver) {
        mutableStateOf<BrowserPage?>(null)
    }
    val uriHandler = LocalUriHandler.current
    var linkOpenFailed by rememberSaveable { mutableStateOf(false) }
    fun openExternal(uri: String) {
        try {
            uriHandler.openUri(uri)
        } catch (_: IllegalArgumentException) {
            linkOpenFailed = true
        }
    }
    fun openCourseDetail(courseId: String) {
        selectedCourseIds = emptyList()
        navigator.openCourseDetail(courseId)
    }
    fun openEditor(courseId: String? = null) {
        if (operationsState.isLoading) return
        selectedCourseIds = emptyList()
        navigator.openCourseEditor(timetable.selectedYear, courseId)
    }
    val refresh = {
        timetableStateHolder.refreshTimetable()
        timetableStateHolder.refreshDailyTimetable()
    }
    val previousYear = {
        navigator.dismissCourseScreens()
        timetableStateHolder.selectPreviousYear()
    }
    val nextYear = {
        navigator.dismissCourseScreens()
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
                    course = course, enabled = !operationsState.isLoading,
                    onEdit = { openEditor(course.id) },
                    onDelete = { deletingCourseId = course.id },
                    onOpenPage = { browserPage = it },
                    onOpenMap = { openExternal(courseMapUrl(course).toString()) },
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
                    scope.launch { searchScroll.scrollToItem(0) }
                },
            )
            AppRoute.Today -> TodayScreen(
                uiState = daily.copy(isLoading = daily.isLoading || authUiState.isLoading),
                onRefresh = timetableStateHolder::refreshDailyTimetable,
            )
            AppRoute.Courses -> CourseSearchScreen(
                year = timetable.selectedYear, draft = searchDraft, onChange = { searchDraft = it }, listState = searchScroll,
                registered = timetable.registeredCourses, isCacheLoaded = timetable.isCacheLoaded, state = operationsState,
                onSearch = timetableStateHolder::searchCourses,
                onLoadMore = timetableStateHolder::loadMoreCourses, onRetry = timetableStateHolder::retryCourseSearch,
                onRegister = { courses ->
                    val year = timetable.selectedYear
                    timetableStateHolder.registerCourses(year, courses.map { it.code }) {
                        if (timetableStateHolder.timetableUiState.value.selectedYear == year) {
                            searchDraft = searchDraft.copy(selected = emptyList())
                            if (navigator.currentRoute == AppRoute.Courses) navigator.returnToTimetable()
                        }
                    }
                },
                onCreate = { openEditor() }, onOpenRegistered = navigator::openRegisteredCourses,
                onManageTags = navigator::openTags, onOpenPage = { browserPage = it },
            )
            AppRoute.RegisteredCourses -> RegisteredCoursesScreen(timetable.selectedYear, timetable.registeredCourses, ::openCourseDetail)
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
            is AppRoute.CourseEditor -> CourseEditorScreen(
                courseId = route.courseId,
                course = timetable.registeredCourses.firstOrNull { it.id == route.courseId },
                year = route.year, tags = timetable.tags, isCacheLoaded = timetable.isCacheLoaded,
                isSaving = operationsState.isLoading, saveFailed = operationsState.operationFailed,
                onCreate = timetableStateHolder::createCourseManually,
                onUpdate = timetableStateHolder::updateCourse,
                onBack = navigator::goBack,
                status = { AppAuthStatus(authUiState) },
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
                    navigator.dismissCourseScreens(course.id)
                }
            },
            onDismiss = { deletingCourseId = null },
        )
    }
    browserPage?.let { page -> BrowserSheet(page, ::openExternal, onDismiss = { browserPage = null }) }
    if (linkOpenFailed) {
        AlertDialog(
            onDismissRequest = { linkOpenFailed = false },
            title = { Text("リンクを開けませんでした") },
            text = { Text("対応するアプリやブラウザを確認してください。") },
            confirmButton = { TextButton(onClick = { linkOpenFailed = false }) { Text("OK") } },
        )
    }
}

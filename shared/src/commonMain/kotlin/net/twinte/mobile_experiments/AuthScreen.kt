package net.twinte.mobile_experiments

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

@Composable
internal fun AuthScreen(
    uiState: AuthUiState,
    isGoogleConfigured: Boolean,
    isAppleConfigured: Boolean,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onUnlinkProvider: (AuthProvider) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    timetableUiState: TimetableUiState,
    onLoadTimetable: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectModule: (Module) -> Unit,
    courseOperationsUiState: CourseOperationsUiState,
    onSearchCourses: (String) -> Unit,
    onRegisterCourse: (Course) -> Unit,
    onCreateCourse: (CreateRegisteredCourseInput, () -> Unit) -> Unit,
    onUpdateCourse: (UpdateRegisteredCourseInput, () -> Unit) -> Unit,
    onDeleteCourse: (RegisteredCourse) -> Unit,
    onCreateTag: (String) -> Unit,
    onUpdateTag: (Tag, String) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onMoveTag: (Tag, Int) -> Unit,
    dailyTimetableUiState: DailyTimetableUiState,
    onLoadDailyTimetable: () -> Unit,
) {
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }
    val loggedInState = uiState.authState as? AuthState.LoggedIn
    val loggedInUser = loggedInState?.user
    val linkedProviders = loggedInUser?.authentications?.map { it.provider }?.toSet().orEmpty()
    val canUnlinkProvider = linkedProviders.size > 1

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Twin:te Auth", style = MaterialTheme.typography.headlineMedium)
        Text(uiState.message, style = MaterialTheme.typography.bodyLarge)
        loggedInState?.session?.sessionId?.let {
            Text("Session: ${it.take(8)}...", style = MaterialTheme.typography.bodyMedium)
        }
        loggedInUser?.let {
            Text("User ID: ${it.id}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Providers: ${it.authentications.joinToString { auth -> auth.provider.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = isGoogleConfigured && !uiState.isLoading,
                onClick = onGoogleSignIn,
            ) {
                Text("Google")
            }
            Button(
                enabled = isAppleConfigured && !uiState.isLoading,
                onClick = onAppleSignIn,
            ) {
                Text("Apple")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = loggedInUser != null && !uiState.isLoading,
                onClick = onRefresh,
            ) {
                Text("getMe")
            }
            OutlinedButton(
                enabled = loggedInUser != null && !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = onSignOut,
            ) {
                Text("Logout")
            }
        }
        loggedInUser?.let {
            TimetableSection(
                authIsLoading = uiState.isLoading,
                timetableUiState = timetableUiState,
                onLoadTimetable = onLoadTimetable,
                onPreviousYear = onPreviousYear,
                onNextYear = onNextYear,
                onSelectModule = onSelectModule,
                courseOperationsUiState = courseOperationsUiState,
                onSearchCourses = onSearchCourses,
                onRegisterCourse = onRegisterCourse,
                onCreateCourse = onCreateCourse,
                onUpdateCourse = onUpdateCourse,
                onDeleteCourse = onDeleteCourse,
                onCreateTag = onCreateTag,
                onUpdateTag = onUpdateTag,
                onDeleteTag = onDeleteTag,
                onMoveTag = onMoveTag,
                dailyTimetableUiState = dailyTimetableUiState,
                onLoadDailyTimetable = onLoadDailyTimetable,
            )
            if (canUnlinkProvider) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (AuthProvider.Google in linkedProviders) {
                        OutlinedButton(
                            enabled = !uiState.isLoading,
                            onClick = { onUnlinkProvider(AuthProvider.Google) },
                        ) {
                            Text("Unlink Google")
                        }
                    }
                    if (AuthProvider.Apple in linkedProviders) {
                        OutlinedButton(
                            enabled = !uiState.isLoading,
                            onClick = { onUnlinkProvider(AuthProvider.Apple) },
                        ) {
                            Text("Unlink Apple")
                        }
                    }
                }
            }
            OutlinedButton(
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = { showDeleteAccountConfirmation = true },
            ) {
                Text("Delete Account")
            }
        }
    }

    if (showDeleteAccountConfirmation) {
        DeleteAccountDialog(
            onConfirm = {
                showDeleteAccountConfirmation = false
                onDeleteAccount()
            },
            onDismiss = { showDeleteAccountConfirmation = false },
        )
    }
}

@Composable
private fun TimetableSection(
    authIsLoading: Boolean,
    timetableUiState: TimetableUiState,
    onLoadTimetable: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectModule: (Module) -> Unit,
    courseOperationsUiState: CourseOperationsUiState,
    onSearchCourses: (String) -> Unit,
    onRegisterCourse: (Course) -> Unit,
    onCreateCourse: (CreateRegisteredCourseInput, () -> Unit) -> Unit,
    onUpdateCourse: (UpdateRegisteredCourseInput, () -> Unit) -> Unit,
    onDeleteCourse: (RegisteredCourse) -> Unit,
    onCreateTag: (String) -> Unit,
    onUpdateTag: (Tag, String) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onMoveTag: (Tag, Int) -> Unit,
    dailyTimetableUiState: DailyTimetableUiState,
    onLoadDailyTimetable: () -> Unit,
) {
    OutlinedButton(
        enabled = !authIsLoading && !timetableUiState.isLoading,
        onClick = onLoadTimetable,
    ) {
        Text("Load Timetable")
    }
    Text(timetableUiState.message, style = MaterialTheme.typography.bodyMedium)
    TimetableSelectors(
        uiState = timetableUiState,
        onPreviousYear = onPreviousYear,
        onNextYear = onNextYear,
        onSelectModule = onSelectModule,
    )
    TimetableGrid(timetableUiState)
    timetableUiState.courseCount?.let { Text("Registered courses: $it") }
    timetableUiState.tagCount?.let { Text("Tags: $it") }
    timetableUiState.lastSuccessfulSyncAt?.let { Text("Last sync: $it") }
    if (timetableUiState.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    CourseOperationsSection(
        selectedYear = timetableUiState.selectedYear,
        registeredCourses = timetableUiState.registeredCourses,
        tags = timetableUiState.tags,
        uiState = courseOperationsUiState,
        onSearch = onSearchCourses,
        onRegister = onRegisterCourse,
        onCreateCourse = onCreateCourse,
        onUpdate = onUpdateCourse,
        onDelete = onDeleteCourse,
        onCreateTag = onCreateTag,
        onUpdateTag = onUpdateTag,
        onDeleteTag = onDeleteTag,
        onMoveTag = onMoveTag,
    )

    OutlinedButton(
        enabled = !authIsLoading && !dailyTimetableUiState.isLoading,
        onClick = onLoadDailyTimetable,
    ) {
        Text("Load Today's Schedule")
    }
    Text(dailyTimetableUiState.message, style = MaterialTheme.typography.bodyMedium)
    Text("Date: ${dailyTimetableUiState.date}", style = MaterialTheme.typography.bodyMedium)
    dailyTimetableUiState.moduleLabel?.let { Text("Module: $it") }
    dailyTimetableUiState.eventLabel?.let { Text("Day: $it") }
    dailyTimetableUiState.lectureCount?.let { Text("Today's courses: $it") }
    dailyTimetableUiState.firstCourseName?.let { Text("First today: $it") }
    dailyTimetableUiState.lastSuccessfulSyncAt?.let { Text("Daily sync: $it") }
    if (dailyTimetableUiState.isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TimetableSelectors(
    uiState: TimetableUiState,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectModule: (Module) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPreviousYear) {
            Text("<")
        }
        Text("${uiState.selectedYear}年度")
        OutlinedButton(onClick = onNextYear) {
            Text(">")
        }
    }
    TeachingModules.chunked(3).forEach { modules ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modules.forEach { module ->
                if (module == uiState.selectedModule) {
                    Button(onClick = { onSelectModule(module) }) {
                        Text(module.label)
                    }
                } else {
                    OutlinedButton(onClick = { onSelectModule(module) }) {
                        Text(module.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableGrid(uiState: TimetableUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            GridCell(
                text = "",
                modifier = Modifier.width(PeriodCellWidth),
                height = HeaderCellHeight,
            )
            TimetableDays.forEach { day ->
                GridCell(
                    text = day.label,
                    modifier = Modifier.weight(1f),
                    height = HeaderCellHeight,
                )
            }
        }
        (1..6).forEach { period ->
            Row(modifier = Modifier.fillMaxWidth()) {
                GridCell(
                    text = period.toString(),
                    modifier = Modifier.width(PeriodCellWidth),
                    height = CourseCellHeight,
                )
                TimetableDays.forEach { day ->
                    CourseGridCell(uiState.cells[TimetableSlot(day, period)].orEmpty())
                }
            }
        }
    }
}

@Composable
private fun RowScope.GridCell(
    text: String,
    modifier: Modifier,
    height: Dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
    }
}

@Composable
private fun RowScope.CourseGridCell(courses: List<TimetableCourseSummary>) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(CourseCellHeight)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (courses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = courses.joinToString(" / ") { it.name },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                val locations = courses.map { it.locations }.filter { it.isNotBlank() }.distinct()
                if (locations.isNotEmpty()) {
                    Text(
                        text = locations.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account?") },
        text = { Text("This permanently deletes your account and cannot be undone.") },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                onClick = onConfirm,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private val TeachingModules = listOf(
    Module.SpringA,
    Module.SpringB,
    Module.SpringC,
    Module.FallA,
    Module.FallB,
    Module.FallC,
)

private val TimetableDays = listOf(
    Day.Monday,
    Day.Tuesday,
    Day.Wednesday,
    Day.Thursday,
    Day.Friday,
)

private val PeriodCellWidth = 28.dp
private val HeaderCellHeight = 30.dp
private val CourseCellHeight = 68.dp

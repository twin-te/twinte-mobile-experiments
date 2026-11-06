package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

@Composable
internal fun CourseOperationsSection(
    selectedYear: Int,
    registeredCourses: List<RegisteredCourse>,
    tags: List<Tag>,
    uiState: CourseOperationsUiState,
    onSearch: (String) -> Unit,
    onRegister: (Course) -> Unit,
    onCreateCourse: (CreateRegisteredCourseInput, () -> Unit) -> Unit,
    onUpdate: (UpdateRegisteredCourseInput, () -> Unit) -> Unit,
    onChangeAttendance: (String, AttendanceCounter, Int) -> Unit,
    onDelete: (RegisteredCourse) -> Unit,
    onManageTags: () -> Unit,
) {
    key(selectedYear) {
        var query by rememberSaveable { mutableStateOf("") }
        var creatingCourse by rememberSaveable { mutableStateOf(false) }
        var editingCourseId by rememberSaveable { mutableStateOf<String?>(null) }
        var attendanceCourseId by rememberSaveable { mutableStateOf<String?>(null) }
        var deletingCourse by remember { mutableStateOf<RegisteredCourse?>(null) }
        val registeredCodes = registeredCourses.mapNotNullTo(mutableSetOf()) { it.code }

        Text("Course search", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading,
                label = { Text("Name or keyword") },
                singleLine = true,
            )
            Button(
                enabled = !uiState.isLoading,
                onClick = { onSearch(query) },
            ) {
                Text("Search")
            }
        }
        uiState.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        uiState.searchResults.forEach { course ->
            CourseSearchResultRow(
                course = course,
                isRegistered = course.code in registeredCodes,
                enabled = !uiState.isLoading,
                onRegister = { onRegister(course) },
            )
        }

        Text("Registered courses ($selectedYear)", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            enabled = !uiState.isLoading,
            onClick = { creatingCourse = true },
        ) {
            Text("Create course")
        }
        if (registeredCourses.isEmpty()) {
            Text("No registered courses", style = MaterialTheme.typography.bodyMedium)
        }
        registeredCourses.forEach { course ->
            RegisteredCourseRow(
                course = course,
                tagNames = tags.filter { it.id in course.tagIds }.map(Tag::name),
                enabled = !uiState.isLoading,
                onEdit = { editingCourseId = course.id },
                onAttendance = { attendanceCourseId = course.id },
                onDelete = { deletingCourse = course },
            )
        }

        OutlinedButton(enabled = !uiState.isLoading, onClick = onManageTags) {
            Text("Manage tags")
        }

        if (creatingCourse) {
            CourseEditorDialog(
                course = null,
                year = selectedYear,
                tags = tags,
                isSaving = uiState.isLoading,
                saveFailed = uiState.operationFailed,
                onCreate = onCreateCourse,
                onUpdate = onUpdate,
                onDismiss = { creatingCourse = false },
            )
        }
        registeredCourses.firstOrNull { it.id == editingCourseId }?.let { course ->
            CourseEditorDialog(
                course = course,
                year = selectedYear,
                tags = tags,
                isSaving = uiState.isLoading,
                saveFailed = uiState.operationFailed,
                onCreate = onCreateCourse,
                onUpdate = onUpdate,
                onDismiss = { editingCourseId = null },
            )
        }
        attendanceCourseId?.let { id ->
            registeredCourses.firstOrNull { it.id == id }?.let { course ->
                CourseAttendanceDialog(
                    course = course,
                    isSaving = uiState.isLoading,
                    operationMessage = uiState.message,
                    onChange = onChangeAttendance,
                    onDismiss = { attendanceCourseId = null },
                )
            }
        }
        deletingCourse?.let { course ->
            AlertDialog(
                onDismissRequest = { deletingCourse = null },
                title = { Text("Delete course?") },
                text = { Text(course.name) },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            deletingCourse = null
                            onDelete(course)
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { deletingCourse = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun CourseSearchResultRow(
    course: Course,
    isRegistered: Boolean,
    enabled: Boolean,
    onRegister: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(course.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(course.code, course.instructors).filter(String::isNotBlank).joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            enabled = enabled && !isRegistered,
            onClick = onRegister,
        ) {
            Text(if (isRegistered) "Added" else "Add")
        }
    }
}

@Composable
private fun RegisteredCourseRow(
    course: RegisteredCourse,
    tagNames: List<String>,
    enabled: Boolean,
    onEdit: () -> Unit,
    onAttendance: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(course.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        course.code?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (tagNames.isNotEmpty()) {
            Text(
                tagNames.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(enabled = enabled, onClick = onEdit) {
                Text("Edit")
            }
            OutlinedButton(enabled = enabled, onClick = onAttendance) {
                Text("Attendance")
            }
            OutlinedButton(
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = onDelete,
            ) {
                Text("Delete")
            }
        }
    }
}

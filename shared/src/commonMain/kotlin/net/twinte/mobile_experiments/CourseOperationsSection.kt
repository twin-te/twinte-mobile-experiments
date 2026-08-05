package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.Course
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
    onUpdate: (UpdateRegisteredCourseInput) -> Unit,
    onDelete: (RegisteredCourse) -> Unit,
    onCreateTag: (String) -> Unit,
    onUpdateTag: (Tag, String) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onMoveTag: (Tag, Int) -> Unit,
) {
    var query by remember(selectedYear) { mutableStateOf("") }
    var editingCourse by remember { mutableStateOf<RegisteredCourse?>(null) }
    var deletingCourse by remember { mutableStateOf<RegisteredCourse?>(null) }
    val registeredCodes = registeredCourses.mapNotNullTo(mutableSetOf()) { it.code }

    HorizontalDivider()
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
    if (registeredCourses.isEmpty()) {
        Text("No registered courses", style = MaterialTheme.typography.bodyMedium)
    }
    registeredCourses.forEach { course ->
        RegisteredCourseRow(
            course = course,
            tagNames = tags.filter { it.id in course.tagIds }.map(Tag::name),
            enabled = !uiState.isLoading,
            onEdit = { editingCourse = course },
            onDelete = { deletingCourse = course },
        )
    }

    TagManagementSection(
        tags = tags,
        enabled = !uiState.isLoading,
        onCreate = onCreateTag,
        onUpdate = onUpdateTag,
        onDelete = onDeleteTag,
        onMove = onMoveTag,
    )

    editingCourse?.let { course ->
        EditRegisteredCourseDialog(
            course = course,
            tags = tags,
            onConfirm = { input ->
                editingCourse = null
                onUpdate(input)
            },
            onDismiss = { editingCourse = null },
        )
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
    onDelete: () -> Unit,
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
        }
        OutlinedButton(enabled = enabled, onClick = onEdit) {
            Text("Edit")
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

@Composable
private fun EditRegisteredCourseDialog(
    course: RegisteredCourse,
    tags: List<Tag>,
    onConfirm: (UpdateRegisteredCourseInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(course.id) { mutableStateOf(course.name) }
    var instructors by remember(course.id) { mutableStateOf(course.instructors) }
    var credit by remember(course.id) { mutableStateOf(course.credit) }
    var memo by remember(course.id) { mutableStateOf(course.memo) }
    var selectedTagIds by remember(course.id) { mutableStateOf(course.tagIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit course") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = instructors,
                    onValueChange = { instructors = it },
                    label = { Text("Instructors") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("Credit") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("Memo") },
                    minLines = 2,
                    maxLines = 4,
                )
                if (tags.isNotEmpty()) {
                    Text("Tags", style = MaterialTheme.typography.titleSmall)
                    tags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = tag.id in selectedTagIds,
                                onCheckedChange = { checked ->
                                    selectedTagIds = if (checked) {
                                        selectedTagIds + tag.id
                                    } else {
                                        selectedTagIds - tag.id
                                    }
                                },
                            )
                            Text(tag.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(
                        UpdateRegisteredCourseInput(
                            id = course.id,
                            name = name,
                            instructors = instructors,
                            credit = credit,
                            memo = memo,
                            tagIds = selectedTagIds.toList(),
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

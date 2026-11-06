package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

@Composable
internal fun CourseEditorDialog(
    course: RegisteredCourse?,
    year: Int,
    tags: List<Tag>,
    isSaving: Boolean,
    saveFailed: Boolean,
    onCreate: (CreateRegisteredCourseInput, () -> Unit) -> Unit,
    onUpdate: (UpdateRegisteredCourseInput, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSerializable(course?.id, year) {
        mutableStateOf(course?.let(CourseEditorDraft::fromCourse) ?: CourseEditorDraft(year))
    }
    var saveAttempted by rememberSaveable(course?.id, year) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (course == null) "Create course" else "Edit course") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.credit,
                    onValueChange = { draft = draft.copy(credit = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Credit (e.g. 1.0)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.instructors,
                    onValueChange = { draft = draft.copy(instructors = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instructors") },
                    singleLine = true,
                )
                Text("Methods", style = MaterialTheme.typography.titleSmall)
                CourseMethod.entries.forEach { method ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = method in draft.methods,
                            onCheckedChange = { checked ->
                                draft = draft.copy(
                                    methods = if (checked) draft.methods + method else draft.methods - method,
                                )
                            },
                        )
                        Text(method.displayName)
                    }
                }
                Text("Schedules", style = MaterialTheme.typography.titleSmall)
                draft.schedules.forEachIndexed { index, schedule ->
                    CourseScheduleEditor(
                        schedule = schedule,
                        onChange = { updated ->
                            draft = draft.copy(
                                schedules = draft.schedules.toMutableList().also { it[index] = updated },
                            )
                        },
                        onRemove = {
                            draft = draft.copy(schedules = draft.schedules.filterIndexed { i, _ -> i != index })
                        },
                    )
                    HorizontalDivider()
                }
                OutlinedButton(
                    onClick = {
                        draft = draft.copy(
                            schedules = draft.schedules + CourseSchedule(
                                draft.schedules.lastOrNull()?.module ?: Module.SpringA,
                                Day.Monday,
                                1,
                                "",
                            ),
                        )
                    },
                ) {
                    Text("Add schedule")
                }
                if (course != null) {
                    OutlinedTextField(
                        value = draft.memo,
                        onValueChange = { draft = draft.copy(memo = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Memo") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    if (tags.isNotEmpty()) {
                        Text("Tags", style = MaterialTheme.typography.titleSmall)
                        tags.forEach { tag ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = tag.id in draft.tagIds,
                                    onCheckedChange = { checked ->
                                        draft = draft.copy(
                                            tagIds = if (checked) draft.tagIds + tag.id else draft.tagIds - tag.id,
                                        )
                                    },
                                )
                                Text(tag.name)
                            }
                        }
                    }
                }
                if (saveAttempted && saveFailed) {
                    Text("Could not save. Try again.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = draft.isValid && !isSaving,
                onClick = {
                    saveAttempted = true
                    if (course == null) {
                        onCreate(draft.toCreateInput(), onDismiss)
                    } else {
                        onUpdate(draft.toUpdateInput(course.id), onDismiss)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CourseScheduleEditor(
    schedule: CourseSchedule,
    onChange: (CourseSchedule) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceMenu(
            value = schedule.module,
            options = editableModules,
            label = Module::label,
            onSelect = { onChange(schedule.copy(module = it)) },
            modifier = Modifier.weight(1f),
        )
        ChoiceMenu(
            value = schedule.day,
            options = editableDays,
            label = Day::label,
            onSelect = { day ->
                onChange(schedule.copy(day = day, period = if (day.isRegular()) 1 else 0))
            },
            modifier = Modifier.weight(1f),
        )
    }
    if (schedule.day.isRegular()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Period")
            IconButton(
                enabled = schedule.period > 1,
                onClick = { onChange(schedule.copy(period = schedule.period - 1)) },
            ) { Text("-") }
            Text(schedule.period.toString())
            IconButton(
                enabled = schedule.period < 8,
                onClick = { onChange(schedule.copy(period = schedule.period + 1)) },
            ) { Text("+") }
        }
    }
    OutlinedTextField(
        value = schedule.locations,
        onValueChange = { onChange(schedule.copy(locations = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Location") },
        singleLine = true,
    )
    OutlinedButton(onClick = onRemove) { Text("Remove schedule") }
}

@Composable
private fun <T> ChoiceMenu(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label(value), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private val CourseMethod.displayName: String
    get() = when (this) {
        CourseMethod.OnlineAsynchronous -> "Online (asynchronous)"
        CourseMethod.OnlineSynchronous -> "Online (live)"
        CourseMethod.FaceToFace -> "In person"
        CourseMethod.Others -> "Other"
    }

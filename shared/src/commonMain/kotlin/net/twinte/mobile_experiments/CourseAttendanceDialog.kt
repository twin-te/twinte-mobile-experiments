package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput

internal enum class AttendanceCounter(val label: String) {
    Attendance("Attendance"),
    Absence("Absence"),
    Late("Late"),
    ;

    fun value(course: RegisteredCourse): Int = when (this) {
        Attendance -> course.attendance
        Absence -> course.absence
        Late -> course.late
    }

    fun updateInput(courseId: String, value: Int): UpdateRegisteredCourseInput = when (this) {
        Attendance -> UpdateRegisteredCourseInput(courseId, attendance = value)
        Absence -> UpdateRegisteredCourseInput(courseId, absence = value)
        Late -> UpdateRegisteredCourseInput(courseId, late = value)
    }
}

@Composable
internal fun CourseAttendanceDialog(
    course: RegisteredCourse,
    isSaving: Boolean,
    operationMessage: String?,
    onChange: (String, AttendanceCounter, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var changeAttempted by remember(course.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(course.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttendanceCounter.entries.forEach { counter ->
                    val value = counter.value(course)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(counter.label, modifier = Modifier.weight(1f))
                        IconButton(
                            enabled = !isSaving && value > 0,
                            onClick = {
                                changeAttempted = true
                                onChange(course.id, counter, -1)
                            },
                        ) { Text("-") }
                        Text(
                            value.toString(),
                            modifier = Modifier.widthIn(min = 36.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            enabled = !isSaving && value < Int.MAX_VALUE,
                            onClick = {
                                changeAttempted = true
                                onChange(course.id, counter, 1)
                            },
                        ) { Text("+") }
                    }
                }
                if (isSaving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (changeAttempted) {
                    operationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            OutlinedButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

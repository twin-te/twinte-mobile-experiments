package net.twinte.mobile_experiments.feature.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun TimetableCourseSelectionDialog(
    courses: List<RegisteredCourse>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (courses.isEmpty()) return
    AlertDialog(
        modifier = Modifier.testTag("course-selection"),
        onDismissRequest = onDismiss,
        containerColor = TwinteTheme.colors.background,
        title = { Text("授業が重複しています") },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                courses.forEach { course ->
                    TwinteButton(
                        course.name, onClick = { onSelect(course.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TwinteButton("閉じる", onClick = onDismiss) },
    )
}

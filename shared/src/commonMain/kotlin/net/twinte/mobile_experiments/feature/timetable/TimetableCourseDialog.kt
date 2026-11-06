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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun TimetableCourseDialog(
    courses: List<RegisteredCourse>,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    canEdit: Boolean,
) {
    if (courses.isEmpty()) return
    val colors = TwinteTheme.colors
    val single = courses.singleOrNull()
    AlertDialog(
        modifier = Modifier.testTag("course-details"),
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = {
            Text(
                single?.name ?: "授業が重複しています",
                color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Medium,
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (single == null) {
                    courses.forEach { course ->
                        TwinteButton(
                            course.name, onClick = { onSelect(course.id) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                } else {
                    Text("${single.credit} 単位", color = colors.muted)
                    if (single.instructors.isNotBlank()) Text(single.instructors, color = colors.text)
                    single.schedules.forEach { schedule ->
                        Text(
                            listOf(
                                schedule.module.label, schedule.day.label,
                                if (schedule.period > 0) "${schedule.period}限" else "",
                                schedule.locations,
                            ).filter(String::isNotBlank).joinToString(" "),
                            color = colors.text,
                        )
                    }
                    Text(
                        "出席 ${single.attendance}・欠席 ${single.absence}・遅刻 ${single.late}",
                        color = colors.muted,
                    )
                    if (single.memo.isNotBlank()) Text(single.memo, color = colors.text)
                }
            }
        },
        confirmButton = {
            if (single != null) {
                TwinteButton("編集", onClick = { onEdit(single.id) }, selected = true, enabled = canEdit)
            }
        },
        dismissButton = { TwinteButton("閉じる", onClick = onDismiss) },
    )
}

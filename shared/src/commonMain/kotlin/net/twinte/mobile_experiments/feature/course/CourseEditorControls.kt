package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.editableDays
import net.twinte.mobile_experiments.editableModules
import net.twinte.mobile_experiments.isRegular
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.TwinteSelectField
import net.twinte.mobile_experiments.ui.components.TwinteTextField
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun EditorSection(label: String, required: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, color = TwinteTheme.colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { heading() },
            )
            if (required) Text(
                "必須", color = Color.White, fontSize = 10.sp,
                modifier = Modifier.background(TwinteTheme.colors.unselected, RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp),
            )
        }
        content()
    }
}
@Composable
internal fun EditorRemovableField(
    label: String, value: String, placeholder: String, enabled: Boolean,
    onChange: (String) -> Unit, onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TwinteTextField(label, value, onChange, enabled, placeholder, modifier = Modifier.weight(1f))
        TwinteIconButton("$label を削除", Lucide.X, onRemove, enabled = enabled, diameter = 24.dp)
    }
}

@Composable
internal fun ScheduleEditor(
    index: Int,
    schedule: CourseSchedule,
    enabled: Boolean,
    onChange: (CourseSchedule) -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (LocalDensity.current.fontScale > 1.4f) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleChoices(index, schedule, enabled, onChange, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleChoices(index, schedule, enabled, onChange, Modifier.weight(1f))
            }
        }
        TwinteIconButton(
            "時限 ${index + 1} を削除", Lucide.X, onRemove, enabled = enabled, diameter = 24.dp,
            modifier = Modifier.padding(top = if (index == 0) 28.dp else 0.dp),
        )
    }
}

@Composable
private fun ScheduleChoices(
    index: Int, schedule: CourseSchedule, enabled: Boolean, onChange: (CourseSchedule) -> Unit, modifier: Modifier,
) {
    TwinteSelectField(
        "学期 ${index + 1}", schedule.module, editableModules, Module::label, enabled, modifier,
        caption = if (index == 0) "学期" else null,
    ) {
        onChange(schedule.copy(module = it))
    }
    TwinteSelectField(
        "曜日 ${index + 1}", schedule.day, editableDays, Day::label, enabled, modifier,
        caption = if (index == 0) "曜日" else null,
    ) { day ->
        onChange(schedule.copy(day = day, period = if (day.isRegular()) schedule.period.coerceIn(1, 8) else 0))
    }
    TwinteSelectField(
        "時限 ${index + 1}", schedule.period, if (schedule.day.isRegular()) (1..8).toList() else listOf(0),
        { if (it == 0) "指定なし" else "${it}限" }, enabled && schedule.day.isRegular(), modifier,
        caption = if (index == 0) "時限" else null,
    ) {
        onChange(schedule.copy(period = it))
    }
}
@Composable
internal fun EditorAddButton(description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(Lucide.Plus, null, Modifier.size(18.dp), tint = TwinteTheme.colors.muted)
        Text("追加する", color = TwinteTheme.colors.muted, modifier = Modifier.padding(start = 8.dp))
    }
}

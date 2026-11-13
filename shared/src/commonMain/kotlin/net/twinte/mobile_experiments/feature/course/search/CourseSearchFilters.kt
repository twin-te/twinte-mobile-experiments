package net.twinte.mobile_experiments.feature.course.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.editableDays
import net.twinte.mobile_experiments.editableModules
import net.twinte.mobile_experiments.isRegular
import net.twinte.mobile_experiments.ui.components.TwinteCheckboxRow
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.TwinteSelectField
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun CourseSearchFilters(draft: CourseSearchDraft, enabled: Boolean, onChange: (CourseSearchDraft) -> Unit) {
    val colors = TwinteTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TwinteCheckboxRow("空いているコマのみを検索", draft.onlyBlank, enabled) { onChange(draft.copy(onlyBlank = it)) }
        if (!draft.onlyBlank) {
            draft.schedules.forEachIndexed { index, schedule ->
                val update: (CourseSearchSchedule) -> Unit = { updated ->
                    onChange(draft.copy(schedules = draft.schedules.mapIndexed { i, current -> if (i == index) updated else current }))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (LocalDensity.current.fontScale > 1.4f) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScheduleChoices(index, schedule, enabled, Modifier.fillMaxWidth(), update)
                        }
                    } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleChoices(index, schedule, enabled, Modifier.weight(1f), update)
                    }
                    if (index == 0) Spacer(Modifier.size(48.dp))
                    else TwinteIconButton("検索条件 ${index + 1} を削除", Lucide.X, {
                        onChange(draft.copy(schedules = draft.schedules.filterIndexed { i, _ -> i != index }))
                    }, enabled = enabled, diameter = 24.dp)
                }
            }
            TextButton(onClick = { onChange(draft.copy(schedules = draft.schedules + CourseSearchSchedule())) }, enabled = enabled) {
                Icon(Lucide.Plus, null, tint = colors.muted)
                Text("条件を追加", color = colors.muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ScheduleChoices(
    index: Int, schedule: CourseSearchSchedule, enabled: Boolean, modifier: Modifier, onChange: (CourseSearchSchedule) -> Unit,
) {
    TwinteSelectField<Module?>("検索学期 ${index + 1}", schedule.module, listOf(null) + editableModules,
        { it?.label ?: "指定なし" }, enabled, modifier, if (index == 0) "学期" else null) {
        onChange(schedule.copy(module = it))
    }
    TwinteSelectField<Day?>("検索曜日 ${index + 1}", schedule.day, listOf(null) + editableDays,
        { it?.label ?: "指定なし" }, enabled, modifier, if (index == 0) "曜日" else null) { day ->
        onChange(schedule.copy(day = day, period = if (day == null || day.isRegular()) schedule.period else null))
    }
    TwinteSelectField<Int?>("検索時限 ${index + 1}", schedule.period, listOf(null) + (1..8).toList(),
        { it?.let { "${it}限" } ?: "指定なし" }, enabled && (schedule.day == null || schedule.day.isRegular()),
        modifier, if (index == 0) "時限" else null) { onChange(schedule.copy(period = it)) }
}

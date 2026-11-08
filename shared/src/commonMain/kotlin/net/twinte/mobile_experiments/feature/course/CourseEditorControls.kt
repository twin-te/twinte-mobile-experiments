package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Check
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
import net.twinte.mobile_experiments.ui.components.twinteInputInset
import net.twinte.mobile_experiments.ui.components.twinteRaised
import net.twinte.mobile_experiments.ui.components.twintePrimaryInset
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun SaveCourseButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(50)
    Button(
        onClick, enabled = enabled, shape = shape,
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f).twinteRaised(shape).background(colors.accentFill, shape),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, contentColor = colors.onAccent,
            disabledContainerColor = Color.Transparent, disabledContentColor = colors.onAccent,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

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
internal fun EditorField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value, onChange, enabled = enabled, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent), keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label }
            .testTag("editor-$label").background(colors.background, shape).twinteInputInset(shape)
            .heightIn(min = if (singleLine) 44.dp else 104.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = colors.unselected)
                field()
            }
        },
    )
}

@Composable
internal fun EditorRemovableField(
    label: String, value: String, placeholder: String, enabled: Boolean,
    onChange: (String) -> Unit, onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EditorField(label, value, onChange, enabled, placeholder, modifier = Modifier.weight(1f))
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
    EditorChoice(
        "学期 ${index + 1}", schedule.module, editableModules, Module::label, enabled, modifier,
        caption = if (index == 0) "学期" else null,
    ) {
        onChange(schedule.copy(module = it))
    }
    EditorChoice(
        "曜日 ${index + 1}", schedule.day, editableDays, Day::label, enabled, modifier,
        caption = if (index == 0) "曜日" else null,
    ) { day ->
        onChange(schedule.copy(day = day, period = if (day.isRegular()) schedule.period.coerceIn(1, 8) else 0))
    }
    EditorChoice(
        "時限 ${index + 1}", schedule.period, if (schedule.day.isRegular()) (1..8).toList() else listOf(0),
        { if (it == 0) "指定なし" else "${it}限" }, enabled && schedule.day.isRegular(), modifier,
        caption = if (index == 0) "時限" else null,
    ) {
        onChange(schedule.copy(period = it))
    }
}

@Composable
private fun <T> EditorChoice(
    label: String, value: T, options: List<T>, display: (T) -> String, enabled: Boolean,
    modifier: Modifier = Modifier, caption: String? = null, onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (caption != null) Text(caption, color = colors.text)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(colors.background, shape).twinteInputInset(shape)
                    .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                    .semantics { contentDescription = label }.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    display(value), color = if (enabled) colors.text else colors.unselected,
                    modifier = Modifier.weight(1f), fontSize = 14.sp,
                )
                Icon(Lucide.ChevronDown, null, Modifier.size(14.dp), tint = colors.muted)
            }
            DropdownMenu(
                expanded, onDismissRequest = { expanded = false }, containerColor = colors.background,
                modifier = Modifier.testTag("editor-menu-$label"),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(display(option), color = colors.text) }, onClick = {
                        expanded = false
                        onSelect(option)
                    })
                }
            }
        }
    }
}

@Composable
internal fun EditorAddButton(description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(Lucide.Plus, null, Modifier.size(18.dp), tint = TwinteTheme.colors.muted)
        Text("追加する", color = TwinteTheme.colors.muted, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun EditorCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).alpha(if (enabled) 1f else 0.4f)
                .then(if (checked) Modifier.background(colors.accentFill, shape).twintePrimaryInset(shape)
                    else Modifier.twinteRaised(shape).background(colors.background, shape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Check, null, Modifier.size(18.dp), tint = if (checked) colors.onAccent else colors.unselected)
        }
        Text(label, color = colors.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

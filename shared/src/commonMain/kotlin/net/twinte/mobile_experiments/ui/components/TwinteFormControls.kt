package net.twinte.mobile_experiments.ui.components

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun TwintePrimaryButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
}

@Composable
internal fun TwinteTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value, onChange, enabled = enabled, singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label }
            .testTag("input-$label").background(colors.background, shape).twinteInputInset(shape)
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
internal fun <T> TwinteSelectField(
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
                modifier = Modifier.testTag("select-menu-$label"),
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
internal fun TwinteCheckboxRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = TwinteTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, interactionSource = remember { MutableInteractionSource() }, indication = null,
                enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwinteCheckMark(checked, enabled)
        Text(label, color = colors.text, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
internal fun TwinteCheckbox(description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(48.dp).semantics { contentDescription = description }
            .toggleable(checked, interactionSource = remember { MutableInteractionSource() }, indication = null,
                enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        contentAlignment = Alignment.Center,
    ) { TwinteCheckMark(checked, enabled) }
}

@Composable
private fun TwinteCheckMark(checked: Boolean, enabled: Boolean) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(26.dp).alpha(if (enabled) 1f else 0.4f)
            .then(if (checked) Modifier.background(colors.accentFill, shape).twintePrimaryInset(shape)
                else Modifier.twinteRaised(shape).background(colors.background, shape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Check, null, Modifier.size(18.dp), tint = if (checked) colors.onAccent else colors.unselected)
    }
}

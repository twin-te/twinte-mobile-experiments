package net.twinte.mobile_experiments.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun Modifier.twinteRaised(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    return dropShadow(shape, Shadow(8.dp, colors.shadowDark, offset = DpOffset(2.dp, 3.dp)))
        .dropShadow(shape, Shadow(9.dp, colors.shadowLight, offset = DpOffset((-2).dp, (-3).dp)))
}

@Composable
internal fun Modifier.twinteInset(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    return innerShadow(shape, Shadow(7.dp, colors.insetDark, offset = DpOffset(2.dp, 3.dp)))
        .innerShadow(shape, Shadow(7.dp, colors.insetLight, offset = DpOffset((-2).dp, (-3).dp)))
}

@Composable
internal fun TwinteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
            .semantics { this.selected = selected }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (selected || pressed) Modifier else Modifier.twinteRaised(shape))
                .then(
                    if (selected) Modifier.background(colors.accentFill, shape)
                    else Modifier.background(colors.background, shape),
                )
                .then(if (selected || pressed) Modifier.twinteInset(shape) else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (!enabled) colors.muted.copy(alpha = 0.6f) else if (selected) {
                    colors.onAccent
                } else colors.text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TwinteIconButton(
    description: String,
    image: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = modifier.size(48.dp).semantics { contentDescription = description }
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(36.dp)
                    .then(if (pressed) Modifier else Modifier.twinteRaised(shape))
                    .background(colors.background, shape)
                    .then(if (pressed) Modifier.twinteInset(shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    image, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (enabled) colors.muted else colors.muted.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
internal fun TwinteSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier.height(40.dp).twinteRaised(shape)
            .background(colors.background, shape).twinteInset(shape).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier.weight(1f).height(40.dp)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(3.dp)
                    .then(if (isSelected) Modifier.background(colors.accentFill, shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (isSelected) colors.onAccent else colors.muted,
                )
            }
        }
    }
}

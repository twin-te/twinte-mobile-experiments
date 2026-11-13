package net.twinte.mobile_experiments.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun Modifier.twinteBaseSurface(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    // Keep the page's bottom edge open, without a highlight above the system gesture area.
    return dropShadow(shape, Shadow(2.dp, colors.shadowLight, offset = DpOffset((-1).dp, (-1).dp)))
        .dropShadow(shape, Shadow(10.dp, colors.shadowDark, offset = DpOffset(2.dp, 4.dp)))
        .background(colors.surface, shape)
        .innerShadow(shape, Shadow(4.dp, colors.insetDark, offset = DpOffset(2.dp, 3.dp)))
}

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
internal fun Modifier.twinteInputInset(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    return innerShadow(shape, Shadow(4.dp, colors.inputInsetDark, offset = DpOffset(1.dp, 1.dp)))
        .innerShadow(shape, Shadow(3.dp, colors.inputInsetLight, offset = DpOffset((-2).dp, (-2).dp)))
}

@Composable
internal fun Modifier.twintePrimaryInset(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    return innerShadow(shape, Shadow(2.dp, colors.primaryInsetDark, offset = DpOffset(1.dp, 2.dp)))
        .innerShadow(shape, Shadow(1.dp, colors.primaryInsetLight, offset = DpOffset((-1).dp, (-2).dp)))
}

@Composable
internal fun Modifier.twinteAccentIcon(): Modifier {
    val brush = TwinteTheme.colors.accentFill
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush, blendMode = BlendMode.SrcIn)
        }
    }
}

@Composable
internal fun Modifier.twintePanelShadow(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    // Draw on an empty background layer so outer shadows do not tint the transparent panel's interior.
    return drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawWithContent { clipPath(path, ClipOp.Difference) { this@onDrawWithContent.drawContent() } }
    }.dropShadow(shape, Shadow(10.dp, colors.shadowDark, offset = DpOffset(2.dp, 4.dp)))
        .dropShadow(shape, Shadow(2.dp, colors.shadowLight, offset = DpOffset((-1).dp, (-1).dp)))
}

@Composable
internal fun Modifier.twintePanelInset(shape: Shape): Modifier {
    val colors = TwinteTheme.colors
    return innerShadow(shape, Shadow(12.dp, colors.insetLight, offset = DpOffset((-6).dp, (-8).dp)))
        .innerShadow(shape, Shadow(4.dp, colors.insetDark, offset = DpOffset(2.dp, 3.dp)))
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
    diameter: Dp = 36.dp,
    accented: Boolean = false,
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
                modifier = Modifier.size(diameter).alpha(if (accented && !enabled) 0.3f else 1f)
                    .then(if (pressed) Modifier else Modifier.twinteRaised(shape))
                    .then(
                        when {
                            accented && pressed -> Modifier.background(colors.accentFill, shape)
                            accented -> Modifier.background(colors.surface, shape)
                            else -> Modifier.background(colors.background, shape)
                        },
                    )
                    .then(if (pressed) Modifier.twinteInset(shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    image, contentDescription = null,
                    modifier = Modifier.size(diameter * (5f / 9f))
                        .then(if (accented && !pressed) Modifier.twinteAccentIcon() else Modifier),
                    tint = if (accented) Color.White else if (enabled) colors.muted else colors.muted.copy(alpha = 0.5f),
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
    val labelStyle = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    Box(
        modifier = modifier.heightIn(min = 48.dp).selectableGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize().padding(vertical = 10.dp).twinteRaised(shape)
                .background(colors.background, shape).twinteInputInset(shape),
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(
                            selected = isSelected, interactionSource = interaction, indication = null,
                            role = Role.RadioButton, onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp)
                            .then(
                                if (isSelected) Modifier.dropShadow(
                                    shape, Shadow(3.dp, colors.shadowDark, offset = DpOffset(2.dp, 2.dp)),
                                ).background(colors.accentFill, shape) else Modifier,
                            ).padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, style = labelStyle, maxLines = 1,
                            color = if (isSelected) colors.onAccent else colors.muted,
                        )
                    }
                }
            }
        }
    }
}

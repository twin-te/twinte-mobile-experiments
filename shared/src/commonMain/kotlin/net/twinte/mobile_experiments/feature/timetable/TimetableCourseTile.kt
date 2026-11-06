package net.twinte.mobile_experiments.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.twinte.mobile_experiments.TimetableCourseSummary
import net.twinte.mobile_experiments.ui.components.twinteInset
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun TimetableCourseTile(
    courses: List<TimetableCourseSummary>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    val first = courses.firstOrNull()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(4.dp)
    val nameStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
    val locationStyle = nameStyle.copy(fontSize = 9.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)
    Box(
        modifier = modifier.background(if (first == null) colors.empty else colors.course, shape)
            .then(if (pressed) Modifier.twinteInset(shape) else Modifier)
            .then(
                if (onClick == null) Modifier else Modifier.clickable(
                    interaction, indication = null, role = Role.Button, onClick = onClick,
                ),
            ),
    ) {
        if (first != null) {
            Column(
                Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        first.name, color = colors.text, style = nameStyle,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (first.locations.isNotBlank()) {
                        Text(
                            first.locations, color = colors.muted, style = locationStyle,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (courses.size > 1) {
                    Text(
                        "他${courses.size - 1}件", color = colors.accentText, style = nameStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().background(colors.background, shape).padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

package net.twinte.mobile_experiments.feature.course.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Shapes
import com.composables.icons.lucide.UserRound
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.feature.course.courseScheduleText
import net.twinte.mobile_experiments.feature.course.detailLabel
import net.twinte.mobile_experiments.feature.course.syllabusUrl
import net.twinte.mobile_experiments.ui.browser.BrowserPage
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteCheckbox
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun CourseSearchResult(
    course: Course, selected: Boolean, registered: Boolean, detailed: Boolean, expanded: Boolean, enabled: Boolean,
    onSelect: () -> Unit, onExpand: () -> Unit, onOpenPage: (BrowserPage) -> Unit,
) {
    val colors = TwinteTheme.colors
    Row(
        Modifier.fillMaxWidth().testTag("search-result-${course.code}")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = Role.Button, onClickLabel = if (expanded) "授業情報を閉じる" else "授業情報を展開", onClick = onExpand)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TwinteCheckbox("${course.code} を選択", selected || registered, enabled && !registered) { onSelect() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(course.code, color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (registered) Text("登録済み", color = colors.accentText, fontSize = 12.sp)
            }
            Text(course.name, color = colors.text, fontWeight = FontWeight.Medium)
            if (detailed || expanded) {
                ResultDetail(Lucide.Clock, courseScheduleText(course.schedules), expanded)
                ResultDetail(Lucide.Banknote, "${course.credit} 単位", expanded)
                ResultDetail(Lucide.UserRound, course.instructors, expanded)
                ResultDetail(Lucide.MapPin, course.schedules.map { it.locations }.filter(String::isNotBlank).distinct().joinToString(" / "), expanded)
                ResultDetail(Lucide.Shapes, course.methods.joinToString("・") { it.detailLabel }, expanded)
                if (course.overview.isNotBlank()) Text(course.overview, color = colors.muted, fontSize = 12.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            if (expanded) {
                if (course.remarks.isNotBlank()) Text(course.remarks, color = colors.muted, fontSize = 12.sp)
                TwinteButton("シラバス", onClick = {
                    onOpenPage(BrowserPage("シラバス", syllabusUrl(course.year, course.code)))
                })
            }
        }
        Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, null,
            modifier = Modifier.padding(horizontal = 8.dp).size(18.dp), tint = colors.muted)
    }
}

@Composable
private fun ResultDetail(icon: ImageVector, value: String, expanded: Boolean) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp), tint = TwinteTheme.colors.muted)
        Text(value, color = TwinteTheme.colors.muted, fontSize = 12.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
    }
}

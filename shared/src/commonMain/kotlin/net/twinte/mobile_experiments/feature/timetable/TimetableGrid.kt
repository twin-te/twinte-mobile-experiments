package net.twinte.mobile_experiments.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.twinte.mobile_experiments.TimetableSlot
import net.twinte.mobile_experiments.TimetableUiState
import net.twinte.mobile_experiments.core.timetable.PeriodTimes
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun TimetableGrid(
    state: TimetableUiState,
    onOpenCourses: (List<String>) -> Unit,
    onOpenEmptySlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = (36 * fontScale).dp
        val headingHeight = (20 * fontScale).dp
        val rowHeight = maxOf((80 * fontScale).dp, (maxHeight - headingHeight - 12.dp) / 6)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("timetable-grid"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = headingHeight),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(labelWidth))
                timetableDays.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            day.label.removeSuffix("曜"), color = colors.muted,
                            fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
                            modifier = Modifier.padding(vertical = 2.dp).testTag("weekday-${day.name}"),
                        )
                    }
                }
            }
            PeriodTimes.regular.forEach { period ->
                Row(
                    Modifier.fillMaxWidth().height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Column(
                        Modifier.width(labelWidth).fillMaxHeight()
                            .background(colors.empty, RoundedCornerShape(4.dp)).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            period.period.toString(), color = colors.muted,
                            fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                        Text(
                            period.start.toString(),
                            color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                        )
                        Box(
                            Modifier.size((14 * fontScale).dp).testTag("period-range-${period.period}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "~", color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp,
                                modifier = Modifier.rotate(90f),
                            )
                        }
                        Text(
                            period.end.toString(),
                            color = colors.muted, fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                        )
                    }
                    timetableDays.forEach { day ->
                        val courses = state.cells[TimetableSlot(day, period.period)].orEmpty()
                        TimetableCourseTile(
                            courses = courses,
                            onClick = {
                                if (courses.isEmpty()) onOpenEmptySlot()
                                else onOpenCourses(courses.map { it.id }.distinct())
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .testTag("slot-${day.name}-${period.period}")
                                .semantics {
                                    if (courses.isEmpty()) contentDescription = "${day.label}${period.period}限に授業を追加"
                                },
                        )
                    }
                }
            }
        }
    }
}

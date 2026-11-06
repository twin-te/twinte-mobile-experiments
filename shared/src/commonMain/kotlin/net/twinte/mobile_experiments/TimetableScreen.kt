package net.twinte.mobile_experiments

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module

@Composable
internal fun TimetableScreen(
    uiState: TimetableUiState,
    onRefresh: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectModule: (Module) -> Unit,
) {
    ScreenColumn {
        RefreshButton(isLoading = uiState.isLoading, onClick = onRefresh)
        Text(uiState.message, style = MaterialTheme.typography.bodyMedium)
        TimetableSelectors(uiState, onPreviousYear, onNextYear, onSelectModule)
        TimetableGrid(uiState)
        uiState.courseCount?.let { Text("Registered courses: $it") }
        uiState.lastSuccessfulSyncAt?.let {
            Text("Last sync: $it", style = MaterialTheme.typography.bodySmall)
        }
        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun TodayScreen(
    uiState: DailyTimetableUiState,
    onRefresh: () -> Unit,
) {
    ScreenColumn {
        RefreshButton(isLoading = uiState.isLoading, onClick = onRefresh)
        Text(uiState.message, style = MaterialTheme.typography.bodyMedium)
        Text("Date: ${uiState.date}")
        uiState.moduleLabel?.let { Text("Module: $it") }
        uiState.eventLabel?.let { Text("Day: $it") }
        uiState.lectureCount?.let { Text("Today's courses: $it") }
        uiState.firstCourseName?.let { Text("First today: $it") }
        uiState.lastSuccessfulSyncAt?.let {
            Text("Last sync: $it", style = MaterialTheme.typography.bodySmall)
        }
        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TimetableSelectors(
    uiState: TimetableUiState,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectModule: (Module) -> Unit,
) {
    TimetableYearSelector(uiState.selectedYear, onPreviousYear, onNextYear)
    TeachingModules.chunked(3).forEach { modules ->
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modules.forEachIndexed { index, module ->
                SegmentedButton(
                    selected = module == uiState.selectedModule,
                    onClick = { onSelectModule(module) },
                    shape = SegmentedButtonDefaults.itemShape(index, modules.size),
                ) { Text(module.label) }
            }
        }
    }
}

@Composable
internal fun TimetableYearSelector(
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton("Previous year", Lucide.ChevronLeft, onClick = onPreviousYear)
        Text("${year}年度")
        AppIconButton("Next year", Lucide.ChevronRight, onClick = onNextYear)
    }
}

@Composable
private fun TimetableGrid(uiState: TimetableUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            GridCell(
                text = "",
                modifier = Modifier.width(PeriodCellWidth),
                height = HeaderCellHeight,
            )
            TimetableDays.forEach { day ->
                GridCell(
                    text = day.label,
                    modifier = Modifier.weight(1f),
                    height = HeaderCellHeight,
                )
            }
        }
        (1..6).forEach { period ->
            Row(modifier = Modifier.fillMaxWidth()) {
                GridCell(
                    text = period.toString(),
                    modifier = Modifier.width(PeriodCellWidth),
                    height = CourseCellHeight,
                )
                TimetableDays.forEach { day ->
                    CourseGridCell(uiState.cells[TimetableSlot(day, period)].orEmpty())
                }
            }
        }
    }
}

@Composable
private fun RowScope.GridCell(
    text: String,
    modifier: Modifier,
    height: Dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
    }
}

@Composable
private fun RowScope.CourseGridCell(courses: List<TimetableCourseSummary>) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(CourseCellHeight)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (courses.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = courses.joinToString(" / ") { it.name },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                val locations = courses.map { it.locations }.filter { it.isNotBlank() }.distinct()
                if (locations.isNotEmpty()) {
                    Text(
                        text = locations.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val TeachingModules = listOf(
    Module.SpringA,
    Module.SpringB,
    Module.SpringC,
    Module.FallA,
    Module.FallB,
    Module.FallC,
)

private val TimetableDays = listOf(
    Day.Monday,
    Day.Tuesday,
    Day.Wednesday,
    Day.Thursday,
    Day.Friday,
)

private val PeriodCellWidth = 28.dp
private val HeaderCellHeight = 30.dp
private val CourseCellHeight = 68.dp

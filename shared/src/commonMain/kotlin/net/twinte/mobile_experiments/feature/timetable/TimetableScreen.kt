package net.twinte.mobile_experiments.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import net.twinte.mobile_experiments.TimetableCourseSummary
import net.twinte.mobile_experiments.TimetableSlot
import net.twinte.mobile_experiments.TimetableUiState
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.timetable.PeriodTimes
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteSegmentedControl
import net.twinte.mobile_experiments.ui.components.twinteInset
import net.twinte.mobile_experiments.ui.components.twinteRaised
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimetableScreen(
    uiState: TimetableUiState,
    currentModule: Module?,
    currentYear: Int,
    onRefresh: () -> Unit,
    onSelectModule: (Module) -> Unit,
    onCurrentModule: () -> Unit,
    onOpenCourses: (List<String>) -> Unit,
) {
    val colors = TwinteTheme.colors
    var special by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().twinteRaised(shape)
                .background(colors.surface, shape).twinteInset(shape)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TwinteSegmentedControl(
                    labels = listOf("通常", "特殊"),
                    selectedIndex = if (special) 1 else 0,
                    onSelect = { special = it == 1 },
                    modifier = Modifier.width(112.dp),
                )
                if (!special) {
                    ModuleSelector(uiState.selectedModule, onSelectModule, Modifier.weight(1f))
                    TwinteButton(
                        "現在の学期", onClick = onCurrentModule,
                        enabled = currentModule in timetableModules,
                        selected = currentModule == uiState.selectedModule && currentYear == uiState.selectedYear,
                        modifier = Modifier.width(112.dp),
                    )
                }
            }
            if (uiState.refreshFailed) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "更新できませんでした。保存した時間割を表示しています。",
                        color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    TwinteButton("再試行", onRefresh, enabled = !uiState.isLoading)
                }
            }
            if (special) {
                SpecialTimetable(
                    groups = remember(uiState.registeredCourses) { specialCourseGroups(uiState.registeredCourses) },
                    onOpenCourses = onOpenCourses,
                    modifier = Modifier.weight(1f),
                )
            } else {
                TimetableGrid(uiState, onOpenCourses, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleSelector(
    selected: Module,
    onSelect: (Module) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = TwinteTheme.colors
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .semantics { contentDescription = "学期を選択" }
                .clickable(role = Role.Button, onClick = { expanded = true }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                selected.label, color = colors.text, fontSize = 16.sp,
                fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Lucide.ChevronDown, null, tint = colors.muted,
                modifier = Modifier.padding(start = 4.dp).width(16.dp),
            )
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            timetableModules.forEach { module ->
                DropdownMenuItem(
                    text = { Text(module.label, color = colors.text) },
                    modifier = Modifier.semantics { this.selected = module == selected },
                    onClick = {
                        expanded = false
                        onSelect(module)
                    },
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    state: TimetableUiState,
    onOpenCourses: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = (32 * fontScale).dp
        val headingHeight = (22 * fontScale).dp
        val rowHeight = maxOf((80 * fontScale).dp, (maxHeight - headingHeight - 12.dp) / 6)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("timetable-grid"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(headingHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(labelWidth))
                timetableDays.forEach { day ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(day.label.removeSuffix("曜"), color = colors.muted, fontSize = 12.sp)
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
                        )
                        Text(
                            "${period.start}\n~\n${period.end}",
                            color = colors.muted, fontSize = 9.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                        )
                    }
                    timetableDays.forEach { day ->
                        val courses = state.cells[TimetableSlot(day, period.period)].orEmpty()
                        TimetableCourseTile(
                            courses = courses,
                            onClick = { onOpenCourses(courses.map { it.id }.distinct()) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .testTag("slot-${day.name}-${period.period}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimetableCourseTile(
    courses: List<TimetableCourseSummary>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    val first = courses.firstOrNull()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier.background(
            if (first == null) colors.empty else colors.course,
            shape,
        ).then(if (pressed) Modifier.twinteInset(shape) else Modifier)
            .then(
                if (first == null) Modifier else Modifier.clickable(
                    interaction, indication = null, role = Role.Button, onClick = onClick,
                ),
            ),
    ) {
        if (first != null) {
            Column(
                Modifier.fillMaxSize().padding(
                    start = 6.dp, end = 6.dp, top = 6.dp, bottom = if (courses.size > 1) 26.dp else 6.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    first.name, color = colors.text, fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (first.locations.isNotBlank()) {
                    Text(
                        first.locations, color = colors.muted, fontSize = 9.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (courses.size > 1) {
                Text(
                    "他${courses.size - 1}件", color = colors.accentText, fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp).fillMaxWidth()
                        .background(colors.background, RoundedCornerShape(4.dp)).padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SpecialTimetable(
    groups: List<SpecialCourseGroup>,
    onOpenCourses: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TwinteTheme.colors
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.label, color = colors.text, fontSize = 14.sp)
                if (group.courses.isEmpty()) {
                    Text("登録なし", color = colors.muted, fontSize = 12.sp)
                }
                group.courses.forEach { course ->
                    Row(
                        Modifier.fillMaxWidth().height(72.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            course.modules, color = colors.muted, fontSize = 11.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(48.dp),
                        )
                        TimetableCourseTile(
                            listOf(TimetableCourseSummary(course.name, course.locations, course.id)),
                            onClick = { onOpenCourses(listOf(course.id)) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

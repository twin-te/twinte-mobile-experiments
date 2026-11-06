package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Shapes
import com.composables.icons.lucide.UserRound
import net.twinte.mobile_experiments.AttendanceCounter
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.twinteAccentIcon
import net.twinte.mobile_experiments.ui.components.twinteInputInset
import net.twinte.mobile_experiments.ui.components.twintePanelInset
import net.twinte.mobile_experiments.ui.components.twintePanelShadow
import net.twinte.mobile_experiments.ui.components.twintePrimaryInset
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun CourseDetailScreen(
    course: RegisteredCourse?,
    tags: List<Tag>,
    isCacheLoaded: Boolean,
    isWorking: Boolean,
    operationFailed: Boolean,
    onChangeAttendance: (AttendanceCounter, Int) -> Unit,
    onToggleTag: (String) -> Unit,
    onCreateTag: (String, () -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val colors = TwinteTheme.colors
    if (course == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).testTag("course-detail-empty"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!isCacheLoaded || isWorking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text("授業が見つかりません", color = colors.text)
                TwinteButton("戻る", onBack)
            }
        }
        return
    }
    var lastAction by remember(course.id) { mutableStateOf<DetailAction?>(null) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 40.dp).testTag("course-detail"),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    course.code?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = colors.muted, fontSize = 12.sp, lineHeight = 12.sp)
                    }
                    Text(
                        course.name, color = colors.text, fontSize = 18.sp, lineHeight = 29.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailRow("開講時限", courseScheduleText(course.schedules), Lucide.Clock)
                    DetailRow("単位数", course.credit.takeIf(String::isNotBlank)?.let { "$it 単位" }.orEmpty(), Lucide.Banknote)
                    DetailRow("担当教員", course.instructors, Lucide.UserRound)
                    DetailRow(
                        "授業場所",
                        course.schedules.map { it.locations }.filter(String::isNotBlank).distinct().joinToString(" / "),
                        Lucide.MapPin,
                    )
                    DetailRow("授業形式", course.methods.distinct().joinToString("・") { it.detailLabel }, Lucide.Shapes)
                }
            }
            CourseTagEditor(
                course, tags, enabled = !isWorking,
                isSaving = isWorking && lastAction == DetailAction.Tags,
                showError = operationFailed && lastAction == DetailAction.Tags,
                onToggle = {
                    lastAction = DetailAction.Tags
                    onToggleTag(it)
                },
                onCreate = { name, onCreated ->
                    lastAction = DetailAction.Tags
                    onCreateTag(name, onCreated)
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("メモ")
                Text(
                    course.memo.ifBlank { "未入力" },
                    color = if (course.memo.isBlank()) colors.muted else colors.text,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp)
                        .background(colors.background, RoundedCornerShape(8.dp))
                        .twinteInputInset(RoundedCornerShape(8.dp)).padding(12.dp),
                )
            }
            AttendanceControls(course, isWorking) { counter, delta ->
                lastAction = DetailAction.Attendance
                onChangeAttendance(counter, delta)
            }
            if (lastAction == DetailAction.Attendance && operationFailed) {
                Text(
                    "出欠を保存できませんでした。通信環境を確認して再度お試しください。",
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("attendance-error"),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = TwinteTheme.colors.muted, fontSize = 12.sp,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector) {
    val colors = TwinteTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(50)
        Box(
            Modifier.size(40.dp).background(colors.course, shape).twintePrimaryInset(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp).twinteAccentIcon())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
            Text(value.ifBlank { "未設定" }, color = colors.text, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AttendanceControls(
    course: RegisteredCourse,
    isWorking: Boolean,
    onChange: (AttendanceCounter, Int) -> Unit,
) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().testTag("attendance-controls")) {
        Box(Modifier.matchParentSize().twintePanelShadow(shape))
        // 48dp touch targets add 10dp around each 28dp button; compensate the outer padding.
        Column(Modifier.fillMaxWidth().twintePanelInset(shape).padding(horizontal = 24.dp, vertical = 6.dp)) {
            AttendanceCounter.entries.forEach { counter ->
                val label = when (counter) {
                    AttendanceCounter.Attendance -> "出席"
                    AttendanceCounter.Absence -> "欠席"
                    AttendanceCounter.Late -> "遅刻"
                }
                val value = counter.value(course)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = colors.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TwinteIconButton(
                        "${label}を減らす", Lucide.Minus, { onChange(counter, -1) }, enabled = !isWorking && value > 0,
                        diameter = 28.dp, accented = true,
                    )
                    Text(
                        value.toString(), modifier = Modifier.width(64.dp).testTag("counter-${counter.name}"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            brush = colors.accentFill, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium,
                        ),
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    TwinteIconButton(
                        "${label}を増やす", Lucide.Plus, { onChange(counter, 1) }, enabled = !isWorking && value < Int.MAX_VALUE,
                        diameter = 28.dp, accented = true,
                    )
                }
            }
        }
        if (isWorking) {
            LinearProgressIndicator(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp).height(4.dp),
            )
        }
    }
}

private enum class DetailAction { Tags, Attendance }

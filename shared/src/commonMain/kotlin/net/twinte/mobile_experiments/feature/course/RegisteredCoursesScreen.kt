package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun RegisteredCoursesScreen(year: Int, courses: List<RegisteredCourse>, onOpenCourse: (String) -> Unit) {
    val colors = TwinteTheme.colors
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Text("${year}年度・${courses.size}件", color = colors.muted, modifier = Modifier.padding(vertical = 16.dp)) }
        if (courses.isEmpty()) item { Text("登録済みの授業がありません。", color = colors.muted) }
        items(courses, key = RegisteredCourse::id) { course ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenCourse(course.id) }.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    course.code?.let { Text(it, color = colors.muted, fontSize = 12.sp) }
                    Text(course.name, color = colors.text, fontWeight = FontWeight.Medium)
                    Text(courseScheduleText(course.schedules).ifBlank { "時限未設定" }, color = colors.muted, fontSize = 12.sp)
                }
                Icon(Lucide.ChevronRight, null, tint = colors.muted)
            }
            HorizontalDivider(color = colors.unselected.copy(alpha = 0.3f))
        }
    }
}

package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.twinteRaised
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CourseDetailActions(
    courseId: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(courseId) { mutableStateOf(false) }
    val colors = TwinteTheme.colors
    Column {
        TwinteIconButton("授業の操作", Lucide.EllipsisVertical, { expanded = !expanded })
        DropdownMenuPopup(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.padding(12.dp)) {
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier.widthIn(min = 160.dp).twinteRaised(shape)
                    .background(colors.surface, shape).padding(vertical = 8.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("編集する", color = if (enabled) colors.text else colors.muted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Lucide.Pencil, null, tint = colors.muted) },
                    enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text("削除する", color = if (enabled) MaterialTheme.colorScheme.error else colors.muted.copy(alpha = 0.6f))
                    },
                    leadingIcon = { Icon(Lucide.Trash2, null, tint = MaterialTheme.colorScheme.error) },
                    enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp),
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
internal fun CourseDeleteDialog(
    course: RegisteredCourse,
    isWorking: Boolean,
    operationFailed: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var attempted by rememberSaveable(course.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("授業の削除") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("「${course.name}」を削除しますか？メモや出欠記録も削除されます。")
                if (attempted && operationFailed) {
                    Text("削除できませんでした。再度お試しください。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isWorking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = {
                    attempted = true
                    onDelete()
                },
            ) { Text("削除") }
        },
        dismissButton = { TextButton(enabled = !isWorking, onClick = onDismiss) { Text("キャンセル") } },
    )
}

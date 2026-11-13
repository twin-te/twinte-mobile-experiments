package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.twinteInputInset
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun CourseTagEditor(
    course: RegisteredCourse,
    tags: List<Tag>,
    enabled: Boolean,
    isSaving: Boolean,
    showError: Boolean,
    onToggle: (String) -> Unit,
    onCreate: (String, () -> Unit) -> Unit,
) {
    val colors = TwinteTheme.colors
    var adding by rememberSaveable(course.id) { mutableStateOf(false) }
    var name by rememberSaveable(course.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canCreate = enabled && name.isNotBlank()
    fun create() {
        if (!canCreate) return
        onCreate(name) {
            adding = false
            name = ""
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(adding) {
        if (adding) focus.requestFocus()
    }
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("タグ", color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
        if (tags.isEmpty()) {
            Text("作成済みのタグがありません。", color = colors.unselected, fontSize = 12.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    val assigned = tag.id in course.tagIds
                    val shape = RoundedCornerShape(4.dp)
                    Box(
                        Modifier.heightIn(min = 32.dp)
                            .toggleable(assigned, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(tag.id) })
                            .testTag("course-tag-${tag.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tag.name, fontSize = 12.sp, lineHeight = 17.sp,
                            color = if (assigned) colors.onAccent else colors.unselected,
                            modifier = Modifier
                                .then(
                                    if (assigned) Modifier.background(colors.accentFill, shape)
                                    else Modifier.border(1.dp, colors.unselected, shape),
                                ).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (adding) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = name, onValueChange = { name = it }, enabled = enabled, singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "タグ名" }
                        .background(colors.background, RoundedCornerShape(6.dp))
                        .twinteInputInset(RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    decorationBox = { field ->
                        Box {
                            if (name.isEmpty()) Text("タグ名", color = colors.unselected)
                            field()
                        }
                    },
                )
                TwinteIconButton("タグを作成", Lucide.Check, ::create, enabled = canCreate, diameter = 28.dp, accented = true)
                TwinteIconButton(
                    "タグの作成をキャンセル", Lucide.X,
                    onClick = {
                        adding = false
                        name = ""
                        focusManager.clearFocus()
                    },
                    enabled = enabled, diameter = 28.dp,
                )
            }
        } else {
            TextButton(onClick = { adding = true }, enabled = enabled) {
                Icon(Lucide.Plus, contentDescription = null, tint = colors.buttonGray, modifier = Modifier.size(16.dp))
                Text(
                    "タグを新たに作成する", color = colors.buttonGray, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (isSaving) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("course-tag-progress"))
        }
        if (showError) {
            Text(
                "タグの保存に失敗しました。再度お試しください。",
                color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.testTag("course-tag-error"),
            )
        }
    }
}

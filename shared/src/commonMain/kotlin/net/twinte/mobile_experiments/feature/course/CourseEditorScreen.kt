package net.twinte.mobile_experiments.feature.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import net.twinte.mobile_experiments.CourseEditorDraft
import net.twinte.mobile_experiments.CourseEditorRoom
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.TwintePageLayout
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun CourseEditorScreen(
    courseId: String?,
    course: RegisteredCourse?,
    year: Int,
    tags: List<Tag>,
    isCacheLoaded: Boolean,
    isSaving: Boolean,
    saveFailed: Boolean,
    onCreate: (CreateRegisteredCourseInput, () -> Unit) -> Unit,
    onUpdate: (UpdateRegisteredCourseInput, () -> Unit) -> Unit,
    onBack: () -> Unit,
    status: @Composable () -> Unit = {},
) {
    if (courseId != null && course == null) {
        EditorPage("授業情報の編集", onBack, status) {
            Column(Modifier.fillMaxSize().padding(24.dp).testTag("course-editor-empty")) {
                if (!isCacheLoaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    Text("授業が見つかりません", color = TwinteTheme.colors.text)
                    TwinteButton("戻る", onBack)
                }
            }
        }
        return
    }
    val original by rememberSerializable(courseId, year) {
        mutableStateOf(course?.let(CourseEditorDraft::fromCourse) ?: CourseEditorDraft(year))
    }
    var draft by rememberSerializable(courseId, year) { mutableStateOf(original) }
    var saveAttempted by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun back() {
        if (isSaving) return
        focus.clearFocus()
        if (draft != original) showDiscard = true else onBack()
    }
    EditorPage(if (courseId == null) "授業の作成" else "授業情報の編集", ::back, status, canGoBack = !isSaving) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("course-editor"),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    EditorSection("授業名", required = true) {
                        EditorField("授業名", draft.name, { draft = draft.copy(name = it) }, !isSaving, "例) ゼミ")
                    }
                    EditorSection("開講時限") {
                        draft.schedules.forEachIndexed { index, schedule ->
                            ScheduleEditor(
                                index, schedule, !isSaving,
                                onChange = { updated -> draft = draft.replaceSchedule(index, updated) },
                                onRemove = { draft = draft.removeSchedule(index) },
                            )
                        }
                        EditorAddButton("開講時限を追加", !isSaving) {
                            draft = draft.copy(schedules = draft.schedules + CourseSchedule(
                                draft.schedules.lastOrNull()?.module ?: Module.SpringA, Day.Monday, 1, "",
                            ))
                        }
                        if (draft.hasDuplicateSchedules) {
                            Text("同じ学期・曜日・時限が重複しています。", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    EditorSection("単位数", required = true) {
                        EditorField(
                            "単位数", draft.credit, { draft = draft.copy(credit = it) }, !isSaving,
                            "例) 1.0", keyboardType = KeyboardType.Decimal,
                        )
                    }
                    EditorSection("担当教員") {
                        draft.instructors.forEachIndexed { index, name ->
                            EditorRemovableField(
                                "担当教員 ${index + 1}", name, "例) 筑波 太郎", !isSaving,
                                onChange = { updated ->
                                    draft = draft.copy(instructors = draft.instructors.mapIndexed { i, current ->
                                        if (i == index) updated else current
                                    })
                                },
                                onRemove = { draft = draft.copy(instructors = draft.instructors.filterIndexed { i, _ -> i != index }) },
                            )
                        }
                        EditorAddButton("担当教員を追加", !isSaving) {
                            draft = draft.copy(instructors = draft.instructors + "")
                        }
                    }
                    EditorSection("授業場所") {
                        draft.rooms.forEachIndexed { index, room ->
                            Column(Modifier.testTag("editor-room-$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                EditorRemovableField(
                                    "授業場所 ${index + 1}", room.name, "例) 研究室", !isSaving,
                                    onChange = { draft = draft.replaceRoom(index, room.copy(name = it)) },
                                    onRemove = { draft = draft.copy(rooms = draft.rooms.filterIndexed { i, _ -> i != index }) },
                                )
                                draft.schedules.forEachIndexed { scheduleIndex, schedule ->
                                    EditorCheck(
                                        courseScheduleText(listOf(schedule)), scheduleIndex in room.scheduleIndices, !isSaving,
                                    ) { checked ->
                                        val indices = if (checked) room.scheduleIndices + scheduleIndex
                                        else room.scheduleIndices - scheduleIndex
                                        draft = draft.replaceRoom(index, room.copy(scheduleIndices = indices))
                                    }
                                }
                            }
                        }
                        EditorAddButton("授業場所を追加", !isSaving) {
                            draft = draft.copy(rooms = draft.rooms + CourseEditorRoom(
                                name = "", scheduleIndices = if (draft.rooms.isEmpty()) draft.schedules.indices.toSet() else emptySet(),
                            ))
                        }
                    }
                    EditorSection("授業形式") {
                        CourseMethod.entries.forEach { method ->
                            EditorCheck(method.detailLabel, method in draft.methods, !isSaving) { checked ->
                                draft = draft.copy(methods = if (checked) draft.methods + method else draft.methods - method)
                            }
                        }
                    }
                    if (courseId != null) {
                        EditorSection("メモ") {
                            EditorField("メモ", draft.memo, { draft = draft.copy(memo = it) }, !isSaving, singleLine = false)
                        }
                        if (tags.isNotEmpty()) EditorSection("タグ") {
                            tags.forEach { tag ->
                                EditorCheck(tag.name, tag.id in draft.tagIds, !isSaving) { checked ->
                                    draft = draft.copy(tagIds = if (checked) draft.tagIds + tag.id else draft.tagIds - tag.id)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (saveAttempted && saveFailed) {
                        Text(
                            "保存できませんでした。通信環境を確認して再度お試しください。",
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("course-editor-error"),
                        )
                    }
                    if (isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    SaveCourseButton(
                        if (courseId == null) "授業を作成" else "変更を保存",
                        enabled = draft.isValid && !isSaving,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("save-course"),
                        onClick = {
                            focus.clearFocus()
                            saveAttempted = true
                            if (courseId == null) onCreate(draft.toCreateInput(), onBack)
                            else onUpdate(draft.toUpdateInput(courseId), onBack)
                        },
                    )
                }
            }
        }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = draft != original || isSaving,
            onBackCompleted = ::back,
        )
    }
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false },
        title = { Text("変更を破棄しますか？") },
        text = { Text("未保存の変更内容は失われます。") },
        confirmButton = {
            TextButton(onClick = {
                showDiscard = false
                onBack()
            }) { Text("破棄する") }
        },
        dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("編集を続ける") } },
    )
}

@Composable
private fun EditorPage(
    title: String, onBack: () -> Unit, status: @Composable () -> Unit,
    canGoBack: Boolean = true, content: @Composable () -> Unit,
) {
    TwintePageLayout(
        leadingWidth = 48.dp, trailingWidth = 48.dp,
        title = {
            Text(title, color = TwinteTheme.colors.text, fontWeight = FontWeight.Medium,
                maxLines = 2, textAlign = TextAlign.Center)
        },
        leading = { TwinteIconButton("Back", Lucide.ArrowLeft, onBack, enabled = canGoBack) }, status = status,
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) { content() }
    }
}

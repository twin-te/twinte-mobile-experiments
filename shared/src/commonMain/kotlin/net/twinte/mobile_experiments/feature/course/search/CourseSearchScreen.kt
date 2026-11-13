package net.twinte.mobile_experiments.feature.course.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import net.twinte.mobile_experiments.CourseOperationsUiState
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.feature.course.courseScheduleText
import net.twinte.mobile_experiments.ui.browser.BrowserPage
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.TwintePrimaryButton
import net.twinte.mobile_experiments.ui.components.TwinteSegmentedControl
import net.twinte.mobile_experiments.ui.components.TwinteTextField
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CourseSearchScreen(
    year: Int,
    draft: CourseSearchDraft,
    onChange: (CourseSearchDraft) -> Unit,
    listState: LazyListState,
    registered: List<RegisteredCourse>,
    isCacheLoaded: Boolean,
    state: CourseOperationsUiState,
    onSearch: (SearchCoursesInput) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRegister: (List<CourseSearchSelection>) -> Unit,
    onCreate: () -> Unit,
    onOpenRegistered: () -> Unit,
    onManageTags: () -> Unit,
    onOpenPage: (BrowserPage) -> Unit,
) {
    val colors = TwinteTheme.colors
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var filtersOpen by rememberSaveable(year) { mutableStateOf(false) }
    var selectionOpen by rememberSaveable(year) { mutableStateOf(false) }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSerializable(year) { mutableStateOf(emptySet<String>()) }
    var unselectCode by rememberSaveable(year) { mutableStateOf<String?>(null) }
    var confirmConflicts by rememberSaveable(year) { mutableStateOf(false) }
    var registrationAttempted by rememberSaveable(year) { mutableStateOf(false) }
    val registeredCodes = registered.filter { it.year == year }.mapNotNullTo(mutableSetOf()) { it.code }
    val selected = draft.selected.filter { it.year == year && it.code !in registeredCodes }
    val busy = state.isLoading
    fun search() {
        if (busy || (draft.onlyBlank && !isCacheLoaded)) return
        focus.clearFocus()
        filtersOpen = false
        registrationAttempted = false
        onSearch(draft.toSearchInput(year, registered))
        scope.launch { listState.scrollToItem(0) }
    }
    fun register(allowConflicts: Boolean = false) {
        if (busy || !isCacheLoaded || selected.isEmpty()) return
        if (!allowConflicts && conflictingCourses(selected, registered).isNotEmpty()) confirmConflicts = true
        else {
            confirmConflicts = false
            registrationAttempted = true
            onRegister(selected)
        }
    }
    LaunchedEffect(year, registeredCodes, isCacheLoaded) {
        if (isCacheLoaded && selected != draft.selected) onChange(draft.copy(selected = selected))
    }
    LaunchedEffect(listState, state.canLoadMore, state.isLoading, state.searchFailed, state.searchResults.size, filtersOpen) {
        if (!state.canLoadMore || state.isLoading || state.searchFailed || filtersOpen) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.isNotEmpty() && (layout.visibleItemsInfo.last().index >= layout.totalItemsCount - 10)
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scrollHeader = maxHeight < 500.dp || LocalDensity.current.fontScale > 1.4f
        val selectionHeight = minOf(180.dp, maxHeight * 0.3f)
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 16.dp).testTag("course-search")) {
            val header: @Composable () -> Unit = {
                SearchHeader(year, draft, !busy, !busy && (!draft.onlyBlank || isCacheLoaded), filtersOpen,
                    onChange = onChange, onSearch = ::search, onToggleFilters = { filtersOpen = !filtersOpen })
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("course-search-list"), state = listState) {
                if (scrollHeader) item(key = "header") { header() } else stickyHeader(key = "header") { header() }
                if (filtersOpen) {
                    item(key = "filters") {
                        CourseSearchFilters(draft, !busy, onChange)
                    }
                } else {
                    if (state.searchFailed) item(key = "search-error") {
                        Column(Modifier.padding(vertical = 12.dp).testTag("search-error")) {
                            Text("検索できませんでした。", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetry, enabled = !busy) { Text("再試行") }
                        }
                    }
                    if (state.isSearching) item(key = "search-loading") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("search-loading"))
                    }
                    if (state.hasSearched && state.searchResults.isEmpty() && !state.isSearching && !state.searchFailed) {
                        item(key = "empty") { Text("一致する授業がありません。", color = colors.muted, modifier = Modifier.padding(vertical = 24.dp)) }
                    }
                    items(state.searchResults, key = Course::id) { course ->
                        CourseSearchResult(
                            course, selected.any { it.code == course.code }, course.code in registeredCodes,
                            draft.detailed, course.id in expanded, !busy,
                            onSelect = { onChange(draft.toggle(course)) },
                            onExpand = { expanded = if (course.id in expanded) expanded - course.id else expanded + course.id },
                            onOpenPage = onOpenPage,
                        )
                        HorizontalDivider(color = colors.unselected.copy(alpha = 0.3f))
                    }
                    if (state.canLoadMore && !state.searchFailed) item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            TwinteButton("さらに読み込む", onLoadMore, enabled = !busy)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            enabled = selected.isNotEmpty(), role = Role.Button) { selectionOpen = !selectionOpen }
                        .testTag("selected-courses-toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("選択中の授業: ${selected.size}件", color = if (selected.isEmpty()) colors.muted else colors.accentText,
                        modifier = Modifier.weight(1f), fontSize = 12.sp)
                    if (selected.isNotEmpty()) Icon(if (selectionOpen) Lucide.ChevronDown else Lucide.ChevronUp,
                        null, Modifier.size(18.dp), tint = colors.muted)
                }
                Box {
                    TwinteIconButton("授業の追加メニュー", Lucide.EllipsisVertical, { toolsOpen = !toolsOpen })
                    DropdownMenu(toolsOpen, onDismissRequest = { toolsOpen = false }, containerColor = colors.background) {
                        listOf(Triple("手動で授業を作成", Lucide.Plus, onCreate),
                            Triple("登録済みの授業", Lucide.BookOpen, onOpenRegistered),
                            Triple("タグを管理", Lucide.Tags, onManageTags)).forEach { (label, icon, action) ->
                            DropdownMenuItem(text = { Text(label, color = colors.text) },
                                leadingIcon = { Icon(icon, null, tint = colors.muted) }, enabled = !busy, onClick = {
                                toolsOpen = false
                                action()
                            })
                        }
                    }
                }
            }
            if (selectionOpen && selected.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = selectionHeight).testTag("selected-courses")) {
                    items(selected, key = CourseSearchSelection::code) { course ->
                        Row(Modifier.fillMaxWidth().testTag("selected-${course.code}"), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(course.name, color = colors.text, fontSize = 12.sp)
                                Text(courseScheduleText(course.schedules), color = colors.muted, fontSize = 10.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TwinteIconButton("${course.code} の選択を解除", Lucide.X, { unselectCode = course.code }, enabled = !busy, diameter = 24.dp)
                        }
                    }
                }
            }
            if (registrationAttempted && state.operationFailed) {
                Text("追加できませんでした。選択内容を残しています。", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("registration-error"), fontSize = 12.sp)
            }
            if (busy && !state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
            TwintePrimaryButton("選択した授業を追加", !busy && isCacheLoaded && selected.isNotEmpty(),
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).testTag("register-courses")) { register() }
        }
    }
    if (confirmConflicts) AlertDialog(
        onDismissRequest = { confirmConflicts = false },
        title = { Text("開講時限が重複しています") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("${conflictingCourses(selected, registered).joinToString("\n") { it.name }}\n\nそのまま追加しますか？")
            }
        },
        confirmButton = { TextButton(onClick = { register(allowConflicts = true) }, enabled = !busy) { Text("そのまま追加") } },
        dismissButton = { TextButton(onClick = { confirmConflicts = false }) { Text("キャンセル") } },
    )
    selected.firstOrNull { it.code == unselectCode }?.let { course ->
        AlertDialog(
            onDismissRequest = { unselectCode = null },
            title = { Text("選択を解除しますか？") }, text = { Text(course.name) },
            confirmButton = { TextButton(onClick = { onChange(draft.deselect(course.code)); unselectCode = null }) { Text("解除") } },
            dismissButton = { TextButton(onClick = { unselectCode = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun SearchHeader(
    year: Int, draft: CourseSearchDraft, enabled: Boolean, canSearch: Boolean, filtersOpen: Boolean,
    onChange: (CourseSearchDraft) -> Unit, onSearch: () -> Unit, onToggleFilters: () -> Unit,
) {
    val colors = TwinteTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.background).padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${year}年度", color = colors.muted, fontSize = 10.sp, modifier = Modifier.testTag("search-year"))
        BoxWithConstraints {
            val fields: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("科目番号", color = colors.muted, fontSize = 12.sp)
                    TwinteTextField("科目番号", draft.code, { onChange(draft.copy(code = it)) }, enabled, "例) GA -0", onSubmit = onSearch)
                }
            }
            val keyword: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("キーワード", color = colors.muted, fontSize = 12.sp)
                    TwinteTextField("キーワード", draft.keyword, { onChange(draft.copy(keyword = it)) }, enabled, "例) 情報 倫理", onSubmit = onSearch)
                }
            }
            if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.4f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fields(Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.Bottom) {
                        keyword(Modifier.weight(1f))
                        TwinteIconButton("検索", Lucide.Search, onSearch, enabled = canSearch)
                    }
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                fields(Modifier.weight(1f))
                keyword(Modifier.weight(1.5f))
                TwinteIconButton("検索", Lucide.Search, onSearch, enabled = canSearch)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TwinteSegmentedControl(listOf("詳細", "簡易"), if (draft.detailed) 0 else 1,
                onSelect = { onChange(draft.copy(detailed = it == 0)) }, modifier = Modifier.weight(1f))
            Row(Modifier.weight(1.5f).heightIn(min = 48.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onToggleFilters)
                .testTag("search-conditions"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                Text("条件", color = colors.muted, fontSize = 12.sp)
                val hasConditions = draft.onlyBlank || draft.schedules.any { !it.isUnspecified }
                val shape = RoundedCornerShape(4.dp)
                Text(draft.conditionLabel, color = if (hasConditions) colors.accentText else colors.muted, fontSize = 10.sp,
                    modifier = Modifier.weight(1f, fill = false)
                        .then(if (hasConditions) Modifier.background(colors.course, shape)
                            else Modifier.border(1.dp, colors.unselected, shape)).padding(4.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(if (filtersOpen) Lucide.ChevronUp else Lucide.ChevronDown, null, Modifier.size(18.dp), tint = colors.muted)
            }
        }
    }
}

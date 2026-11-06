package net.twinte.mobile_experiments

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.components.twinteInset
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.compose.resources.painterResource
import twinte_mobile_experiments.shared.generated.resources.Res
import twinte_mobile_experiments.shared.generated.resources.twinte_logo_dark
import twinte_mobile_experiments.shared.generated.resources.twinte_logo_light

@Composable
internal fun AppScaffold(
    authUiState: AuthUiState,
    selectedYear: Int,
    calendarDate: LocalDate,
    calendarLabel: String?,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    navigator: AppNavigator = rememberAppNavigator(),
    detailActions: @Composable (AppRoute.CourseDetail) -> Unit = {},
    content: @Composable (AppRoute) -> Unit,
) {
    val colors = TwinteTheme.colors
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun navigate(tab: AppTab) {
        scope.launch {
            drawer.close()
            navigator.selectTab(tab)
        }
    }
    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize().background(colors.surface),
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(288.dp),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                drawerContainerColor = colors.background,
                drawerTonalElevation = 0.dp,
            ) {
                Column(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    TwinteButton(
                        "アカウント", onClick = { navigate(AppTab.Account) },
                        selected = navigator.selectedTab == AppTab.Account,
                        modifier = Modifier.fillMaxWidth().testTag("destination-Account"),
                    )
                    Text("設定中の年度", color = colors.muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TwinteIconButton("Previous year", Lucide.ChevronLeft, onPreviousYear)
                        Text(
                            "${selectedYear}年度", modifier = Modifier.weight(1f).testTag("drawer-year"),
                            textAlign = TextAlign.Center, color = colors.text,
                        )
                        TwinteIconButton("Next year", Lucide.ChevronRight, onNextYear)
                    }
                    AppTab.entries.filter { it != AppTab.Account }.forEach { tab ->
                        DrawerDestination(
                            tab = tab, selected = navigator.selectedTab == tab,
                            onClick = { navigate(tab) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    TwinteButton(
                        "時間割を更新", enabled = !isRefreshing,
                        onClick = {
                            onRefresh()
                            scope.launch { drawer.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        val entryContent: @Composable (AppRoute) -> Unit = { route ->
            AppScreenFrame(
                route = route, authUiState = authUiState,
                calendarDate = calendarDate, calendarLabel = calendarLabel,
                onBack = navigator::goBack, onOpenMenu = { scope.launch { drawer.open() } },
                detailActions = { if (route is AppRoute.CourseDetail) detailActions(route) },
            ) {
                if (route == AppRoute.Timetable) content(route) else BottomInsetScreen { content(route) }
            }
        }
        NavDisplay(
            entries = navigator.entries(entryProvider {
                entry<AppRoute.Timetable> { entryContent(it) }
                entry<AppRoute.Today> { entryContent(it) }
                entry<AppRoute.Courses> { entryContent(it) }
                entry<AppRoute.Account> { entryContent(it) }
                entry<AppRoute.Tags> { entryContent(it) }
                entry<AppRoute.CourseDetail> { entryContent(it) }
            }),
            onBack = navigator::goBack,
            transitionSpec = {
                ContentTransform(
                    slideInHorizontally(tween(250)) { it },
                    slideOutHorizontally(tween(250)) { -it / 4 },
                    targetContentZIndex = 1f, sizeTransform = null,
                )
            },
            popTransitionSpec = { backScreenTransform() },
            predictivePopTransitionSpec = { backScreenTransform() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AppScreenFrame(
    route: AppRoute,
    authUiState: AuthUiState,
    calendarDate: LocalDate,
    calendarLabel: String?,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    detailActions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = TwinteTheme.colors
    val isNested = route is AppRoute.CourseDetail || route == AppRoute.Tags
    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.surface).testTag("screen-$route"),
        containerColor = Color.Transparent,
        // Screens inset their content at the bottom, not their background.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(76.dp)) {
                        if (isNested) {
                            TwinteIconButton("Back", Lucide.ArrowLeft, onBack)
                        } else {
                            TwinteIconButton("メニュー", Lucide.Menu, onOpenMenu)
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (route == AppRoute.Timetable) {
                            Image(
                                painterResource(
                                    if (colors.isDark) Res.drawable.twinte_logo_dark
                                    else Res.drawable.twinte_logo_light,
                                ),
                                contentDescription = "Twin:te",
                                modifier = Modifier.width(99.dp).height(27.dp),
                            )
                        } else {
                            Text(
                                when (route) {
                                    AppRoute.Tags -> "タグ"
                                    is AppRoute.CourseDetail -> "授業詳細"
                                    else -> AppTab.entries.first { it.root == route }.title
                                },
                                color = colors.text, fontWeight = FontWeight.Medium,
                                maxLines = 2, textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Column(Modifier.width(88.dp), horizontalAlignment = Alignment.End) {
                        if (route == AppRoute.Timetable) {
                            Text(
                                "${calendarDate.month.number}/${calendarDate.day} (${weekdayLabels[calendarDate.dayOfWeek.ordinal]})",
                                color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                calendarLabel ?: "日課未取得", color = colors.muted, fontSize = 10.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        } else if (route is AppRoute.CourseDetail) {
                            detailActions()
                        } else if (isNested) {
                            TwinteIconButton("メニュー", Lucide.Menu, onOpenMenu)
                        }
                    }
                }
                val offline = (authUiState.authState as? AuthState.LoggedIn)?.session?.isOffline == true
                if (offline || authUiState.message != "Signed in") {
                    Text(
                        if (offline) "オフライン・保存したデータを表示" else authUiState.message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = colors.muted, fontSize = 12.sp,
                    )
                }
                if (authUiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            content()
        }
    }
}

@Composable
private fun BottomInsetScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
        content()
    }
}

@Composable
private fun DrawerDestination(tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val colors = TwinteTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val image = when (tab) {
        AppTab.Timetable -> Lucide.House
        AppTab.Today -> Lucide.CalendarDays
        else -> Lucide.BookOpen
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(if (selected) colors.course else Color.Transparent, shape)
            .then(if (selected) Modifier.twinteInset(shape) else Modifier)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .testTag("destination-${tab.name}").padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(image, contentDescription = null, tint = if (selected) colors.accentText else colors.muted)
        Text(tab.title, color = if (selected) colors.accentText else colors.muted)
    }
}

@Composable
internal fun ScreenColumn(
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun RefreshButton(isLoading: Boolean, onClick: () -> Unit) {
    TwinteButton("Refresh", onClick = onClick, enabled = !isLoading)
}

@Composable
internal fun AppIconButton(description: String, image: ImageVector, onClick: () -> Unit) {
    TwinteIconButton(description, image, onClick)
}

private val weekdayLabels = listOf("月", "火", "水", "木", "金", "土", "日")

private fun backScreenTransform() = ContentTransform(
    slideInHorizontally(tween(250)) { -it / 4 },
    slideOutHorizontally(tween(250)) { it },
    targetContentZIndex = -1f, sizeTransform = null,
)

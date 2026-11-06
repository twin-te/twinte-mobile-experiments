package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Table
import com.composables.icons.lucide.UserRound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScaffold(
    authUiState: AuthUiState,
    navigator: AppNavigator = rememberAppNavigator(),
    content: @Composable (AppRoute) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(if (navigator.currentRoute == AppRoute.Tags) "Tags" else navigator.selectedTab.title)
                    },
                    navigationIcon = {
                        if (navigator.isNested) {
                            AppIconButton("Back", Lucide.ArrowLeft, onClick = navigator::goBack)
                        }
                    },
                )
                if (authUiState.message != "Signed in") {
                    Text(
                        authUiState.message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (authUiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab-${tab.name}"),
                        selected = navigator.selectedTab == tab,
                        onClick = { navigator.selectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            entries = navigator.entries(entryProvider {
                entry<AppRoute.Timetable> { content(it) }
                entry<AppRoute.Today> { content(it) }
                entry<AppRoute.Courses> { content(it) }
                entry<AppRoute.Account> { content(it) }
                entry<AppRoute.Tags> { content(it) }
            }),
            onBack = navigator::goBack,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
        )
    }
}

@Composable
internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun RefreshButton(isLoading: Boolean, onClick: () -> Unit) {
    OutlinedButton(enabled = !isLoading, onClick = onClick) {
        Icon(Lucide.RefreshCw, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Refresh")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIconButton(
    description: String,
    image: ImageVector,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(image, contentDescription = description)
        }
    }
}

private val AppTab.icon: ImageVector
    get() = when (this) {
        AppTab.Timetable -> Lucide.Table
        AppTab.Today -> Lucide.CalendarDays
        AppTab.Courses -> Lucide.BookOpen
        AppTab.Account -> Lucide.UserRound
    }

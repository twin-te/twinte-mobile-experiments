package net.twinte.mobile_experiments.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@Composable
internal fun TwintePageLayout(
    title: @Composable () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingWidth: Dp = 76.dp,
    trailingWidth: Dp = 88.dp,
    background: Brush? = null,
    trailing: @Composable () -> Unit = {},
    status: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().background(background ?: TwinteTheme.colors.surface),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(leadingWidth)) { leading() }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { title() }
                    Box(Modifier.width(trailingWidth), contentAlignment = Alignment.CenterEnd) { trailing() }
                }
                status()
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) { content() }
    }
}

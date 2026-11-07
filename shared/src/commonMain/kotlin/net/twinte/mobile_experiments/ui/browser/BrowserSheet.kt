package net.twinte.mobile_experiments.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import net.twinte.mobile_experiments.ui.components.TwinteButton
import net.twinte.mobile_experiments.ui.components.TwinteIconButton
import net.twinte.mobile_experiments.ui.theme.TwinteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserSheet(page: BrowserPage, onOpenExternal: (String) -> Unit, onDismiss: () -> Unit) {
    val controller = rememberBrowserController(page.url)
    val state = controller.state
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val colors = TwinteTheme.colors
    fun dismiss() {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismiss()
        }
    }
    fun back() {
        if (!controller.goBack()) dismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = state.isAtTop,
        containerColor = colors.background,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).testTag("browser-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TwinteIconButton("戻る", Lucide.ArrowLeft, ::back)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(page.title, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.url.host, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TwinteIconButton("ブラウザで開く", Lucide.ExternalLink, { onOpenExternal(controller.currentUrl.toString()) })
                TwinteIconButton("閉じる", Lucide.X, ::dismiss)
            }
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxSize().background(Color.White)) {
                BrowserView(controller, onOpenExternal, Modifier.fillMaxSize().testTag("embedded-webview"))
                if (state.failed) {
                    Column(
                        Modifier.fillMaxSize().background(colors.background).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("ページを読み込めませんでした。", color = colors.text)
                        TwinteButton("再読み込み", controller::reload)
                    }
                }
            }
        }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = ::back,
        )
    }
}

package net.twinte.mobile_experiments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.feature.timetable.TimetableScreen
import net.twinte.mobile_experiments.ui.components.twinteBaseSurface
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TwinteAppearanceUiTest {
    @Test
    fun timetablePaintsBehindTheGestureAreaButKeepsCoursesAboveIt() {
        listOf(false, true).forEach { dark ->
            listOf(0, 24, 34, 48).forEach { inset ->
                runSkikoComposeUiTest(size = Size(390f, 844f), density = Density(1f)) {
                    setContent {
                        TwinteTheme(darkTheme = dark) {
                            Box(Modifier.fillMaxSize()) {
                                TimetablePreview(bottomInset = inset.dp)
                                if (inset > 0) {
                                    Box(
                                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(inset.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            Modifier.size(134.dp, 5.dp)
                                                .background(TwinteTheme.colors.text, RoundedCornerShape(50)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    assertEquals(844f, onNodeWithTag("timetable-frame").fetchSemanticsNode().boundsInRoot.bottom)
                    assertTrue(
                        onNodeWithTag("slot-Friday-6").fetchSemanticsNode().boundsInRoot.bottom <= 844f - inset - 12f,
                        "Courses must stay above the system area ($inset dp)",
                    )
                    val pixels = captureToImage().toPixelMap()
                    val footerTop = 844 - maxOf(inset, 12)
                    val footerColor = pixels[60, footerTop]
                    for (y in footerTop until 844) {
                        val pixel = pixels[60, y]
                        assertTrue(
                            pixel.red <= footerColor.red + 2f / 255f &&
                                pixel.green <= footerColor.green + 2f / 255f &&
                                pixel.blue <= footerColor.blue + 2f / 255f,
                            "No bright bottom inset shadow in the system area (dark=$dark, inset=$inset, y=$y)",
                        )
                    }
                    if (inset == 34) {
                        val file = File("build/reports/timetable-ui/${if (dark) "dark" else "light"}-gesture-area.png")
                        file.parentFile.mkdirs()
                        Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                            image.encodeToData()!!.use { data -> file.writeBytes(data.bytes) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun periodRangeSeparatorIsVertical() = runSkikoComposeUiTest(
        size = Size(1170f, 2532f), density = Density(3f),
    ) {
        setContent { TwinteTheme(darkTheme = false) { TimetablePreview() } }
        val bounds = onNodeWithTag("period-range-1").fetchSemanticsNode().boundsInRoot
        val pixels = captureToImage().toPixelMap()
        var minX = pixels.width
        var minY = pixels.height
        var maxX = -1
        var maxY = -1
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (pixels[x, y].red < 0.65f) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue(maxX >= 0, "The range separator must be visible")
        assertTrue(
            maxY - minY > maxX - minX,
            "The tilde must be taller than it is wide: ${maxX - minX + 1}x${maxY - minY + 1}",
        )
    }

    @Test
    fun logoIsMonochromeOnlyInDarkTheme() {
        listOf(false, true).forEach { dark ->
            runSkikoComposeUiTest(size = Size(390f, 844f), density = Density(1f)) {
                setContent { TwinteTheme(darkTheme = dark) { TestAuthenticatedApp() } }
                val pixels = onNodeWithContentDescription("Twin:te").captureToImage().toPixelMap()
                var brightPixels = 0
                var coloredPixels = 0
                var webLogoColorPixels = 0
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) {
                        val pixel = pixels[x, y]
                        val max = maxOf(pixel.red, pixel.green, pixel.blue)
                        val min = minOf(pixel.red, pixel.green, pixel.blue)
                        if (max > 0.5f) {
                            brightPixels++
                            if (max - min > 0.05f) coloredPixels++
                        }
                        if (min > 0.8f && max < 0.83f) webLogoColorPixels++
                    }
                }
                assertTrue(brightPixels > 40, "Logo is missing")
                if (dark) {
                    assertTrue(coloredPixels == 0, "Dark logo must not retain the cyan symbol")
                    assertTrue(webLogoColorPixels > 40, "Dark logo must use Web's #CFCFCF")
                } else {
                    assertTrue(coloredPixels > 40, "Light logo must keep its colored symbol")
                }
            }
        }
    }

    @Test
    fun darkBaseShadowHasANarrowTopHighlight() = runSkikoComposeUiTest(
        size = Size(360f, 480f), density = Density(1f),
    ) {
        var showFrame by mutableStateOf(false)
        setContent {
            TwinteTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().background(TwinteTheme.colors.surface)) {
                    if (showFrame) {
                        Box(
                            Modifier.offset(60.dp, 64.dp).size(200.dp, 300.dp)
                                .twinteBaseSurface(RoundedCornerShape(16.dp)),
                        )
                    }
                }
            }
        }
        val background = captureToImage().toPixelMap()
        runOnIdle { showFrame = true }
        val framed = captureToImage().toPixelMap()
        var glow = 0f
        for (y in 48 until 58) {
            for (x in 120 until 200) {
                val before = background[x, y]
                val after = framed[x, y]
                glow = maxOf(glow, after.red - before.red, after.green - before.green, after.blue - before.blue)
            }
        }
        assertTrue(glow <= 2f / 255f, "Frame must not cast a broad bright halo above its top edge: $glow")
        assertTrue(
            background[160, 66].red - framed[160, 66].red > 3f / 255f,
            "The narrow inset at the top must still be visible",
        )
    }
}

@Composable
private fun TimetablePreview(bottomInset: Dp = 0.dp) {
    TimetableScreen(
        uiState = TimetableUiState(selectedYear = 2026),
        currentModule = Module.FallB, currentYear = 2026,
        onRefresh = {}, onSelectModule = {}, onCurrentModule = {}, onOpenCourses = {},
        contentWindowInsets = WindowInsets(bottom = bottomInset),
    )
}

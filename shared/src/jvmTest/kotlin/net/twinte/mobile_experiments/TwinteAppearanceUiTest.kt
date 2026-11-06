package net.twinte.mobile_experiments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.twinte.mobile_experiments.ui.components.twinteBaseSurface
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TwinteAppearanceUiTest {
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

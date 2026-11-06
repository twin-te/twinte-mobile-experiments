package net.twinte.mobile_experiments

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CourseDetailTransitionUiTest {
    @Test
    fun returningHomeMovesTheHeaderAndBodyTogetherWithoutScaling() = runSkikoComposeUiTest(
        size = Size(390f, 844f), density = Density(1f),
    ) {
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = testTimetableCourses.take(1)))
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        val header = onNodeWithText("授業詳細")
        val body = onNodeWithTag("course-detail")
        val headerBefore = header.getUnclippedBoundsInRoot()
        val bodyBefore = body.getUnclippedBoundsInRoot()

        mainClock.autoAdvance = false
        onNodeWithContentDescription("Back").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        val headerDuring = header.getUnclippedBoundsInRoot()
        val bodyDuring = body.getUnclippedBoundsInRoot()
        val offset = bodyDuring.left.value - bodyBefore.left.value
        assertTrue(offset > 1f, "The outgoing screen must move to the right")
        assertEquals(offset, headerDuring.left.value - headerBefore.left.value, 1f)
        assertEquals((headerBefore.right - headerBefore.left).value, (headerDuring.right - headerDuring.left).value, 1f)
        assertEquals((bodyBefore.right - bodyBefore.left).value, (bodyDuring.right - bodyDuring.left).value, 1f)

        val file = File("build/reports/course-detail-ui/back-transition.png")
        file.parentFile.mkdirs()
        Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData()!!.use { file.writeBytes(it.bytes) }
        }
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithContentDescription("Twin:te").assertIsDisplayed()
        onNodeWithTag("course-detail").assertDoesNotExist()
    }
}

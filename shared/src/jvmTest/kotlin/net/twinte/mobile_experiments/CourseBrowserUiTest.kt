package net.twinte.mobile_experiments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.feature.course.courseMapUrl
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CourseBrowserUiTest {
    @Test
    fun anUnavailableExternalAppShowsAnErrorWithoutClosingTheDetails() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(course)))
        setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { throw IllegalArgumentException("No handler") }
            }) {
                TwinteTheme { TestAuthenticatedApp(service = service) }
            }
        }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("地図").performClick()
        onNodeWithText("リンクを開けませんでした").assertIsDisplayed()
        onNodeWithText("OK").performClick()
        onNodeWithTag("course-detail").assertIsDisplayed()
    }

    @Test
    fun syllabusOpensInASheetAndClosingItPreservesTheCourseDetails() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(course)))
        val handler = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                TwinteTheme { TestAuthenticatedApp(service = service) }
            }
        }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").assertIsDisplayed()
        onNodeWithText("シラバス").assertIsDisplayed().performClick()
        onNodeWithTag("browser-sheet").assertIsDisplayed()
        onNodeWithTag("embedded-webview").assertIsDisplayed()
        onNodeWithText("kdb.tsukuba.ac.jp").assertIsDisplayed()
        onNodeWithContentDescription("戻る").assertIsEnabled()
        runOnIdle { assertEquals(emptyList(), opened) }
        onNodeWithContentDescription("ブラウザで開く").performClick()
        runOnIdle { assertEquals(listOf("https://kdb.tsukuba.ac.jp/syllabi/2026/GB12345/jpn/"), opened) }
        onNodeWithContentDescription("戻る").performClick()
        onNodeWithTag("browser-sheet").assertDoesNotExist()
        onNodeWithTag("course-detail").assertIsDisplayed()
    }

    @Test
    fun mapOpensOutsideTheAppInsteadOfCreatingABrowserSheet() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(course)))
        setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened += uri }
            }) {
                TwinteTheme { TestAuthenticatedApp(service = service) }
            }
        }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("地図").performClick()
        onNodeWithTag("browser-sheet").assertDoesNotExist()
        runOnIdle { assertEquals(listOf(courseMapUrl(course).toString()), opened) }
    }

    @Test
    fun manuallyCreatedCoursesHideSyllabusAndResponSheetSurvivesRecreation() = runComposeUiTest {
        val restoration = SaveableComposition()
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(course.copy(code = null))))
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(service = service) } } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("シラバス").assertDoesNotExist()
        onNodeWithText("出席(respon)").performClick()
        onNodeWithText("atmnb.tsukuba.ac.jp").assertIsDisplayed()
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }
        onNodeWithTag("browser-sheet").assertIsDisplayed()
        onNodeWithText("atmnb.tsukuba.ac.jp").assertIsDisplayed()
        onNodeWithContentDescription("閉じる").performClick()
        onNodeWithTag("course-detail").assertIsDisplayed()
    }

    @Test
    fun browserFrameAndTheMenuFitBothThemesAndLargeText() {
        listOf(false to 1f, true to 1f, false to 2f).forEach { (dark, scale) ->
            runSkikoComposeUiTest(size = Size(390f, 844f), density = Density(1f, scale)) {
                val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(course)))
                setContent { TwinteTheme(darkTheme = dark) { TestAuthenticatedApp(service = service) } }
                onNodeWithTag("slot-Monday-2").performScrollTo().performClick()
                onNodeWithContentDescription("授業の操作").performClick()
                onNodeWithText("削除する").performScrollTo().assertIsDisplayed()
                onNodeWithText("シラバス").performScrollTo().performClick()
                onNodeWithContentDescription("閉じる").assertIsDisplayed()
                onNodeWithText("kdb.tsukuba.ac.jp").assertIsDisplayed()
                val file = File("build/reports/course-detail-ui/browser-frame-$dark-$scale.png")
                file.parentFile.mkdirs()
                Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                    image.encodeToData()!!.use { file.writeBytes(it.bytes) }
                }
            }
        }
    }

    private val course = testTimetableCourses.first().copy(code = "GB12345")
}

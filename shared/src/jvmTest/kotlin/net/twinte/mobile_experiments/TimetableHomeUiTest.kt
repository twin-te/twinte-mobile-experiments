package net.twinte.mobile_experiments

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.feature.timetable.timetableDays
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TimetableHomeUiTest {
    @Test
    fun weekdayHeadersFitTheirTextAtNormalAndLargeFontSizes() {
        listOf(1f, 1.3f, 2f).forEach { fontScale ->
            runSkikoComposeUiTest(size = Size(320f, 640f), density = Density(1f, fontScale)) {
                setContent { TwinteTheme { TestAuthenticatedApp() } }
                timetableDays.forEach { day ->
                    val label = onNodeWithTag("weekday-${day.name}").assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertFalse(
                        layout.didOverflowHeight,
                        "${day.label} is vertically clipped at fontScale=$fontScale",
                    )
                    assertTrue(
                        layout.getLineBottom(0) <= layout.size.height,
                        "${day.label} line exceeds its height at fontScale=$fontScale",
                    )
                }
            }
        }
    }

    @Test
    fun duplicateTileOpensCourseSelectionThenTheExistingEditor() = runComposeUiTest {
        setContent { TwinteTheme { TestAuthenticatedApp(withCourse = true) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithText("授業が重複しています").assertIsDisplayed()
        onNode(hasText("情報メディア実験A") and hasAnyAncestor(hasTestTag("course-details"))).performClick()
        onNodeWithText("1.5 単位").assertIsDisplayed()
        onNodeWithText("編集").performClick()
        onNodeWithText("Name", substring = false).assertTextContains("情報メディア実験A")
        onNodeWithText("Cancel").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
    }

    @Test
    fun modulePopupSelectsAndRestoresTheChosenModule() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp() } } }
        onNodeWithContentDescription("学期を選択").performClick()
        onNodeWithTag("module-menu").assertIsDisplayed()
        onNode(hasText("秋B") and hasAnyAncestor(hasTestTag("module-menu"))).assertIsSelected()
        onNodeWithText("秋C").performClick()
        onNodeWithTag("module-menu").assertDoesNotExist()
        onNodeWithText("秋C").assertIsDisplayed()

        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }
        onNodeWithText("秋C").assertIsDisplayed()
        onNodeWithTag("module-menu").assertDoesNotExist()
    }

    @Test
    fun specialSelectionAndItsCachedCoursesSurviveRecreation() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(withCourse = true) } } }
        onNodeWithText("特殊").performClick()
        onNodeWithTag("special-empty-応談").assertIsDisplayed()
        onNodeWithText("登録なし").assertDoesNotExist()
        onNodeWithText("集中講義・情報学特別演習").assertIsDisplayed()
        onNodeWithText("土曜演習").performScrollTo().assertIsDisplayed()
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }
        onNodeWithText("特殊").assertIsSelected()
        onNodeWithText("集中講義・情報学特別演習").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun currentModuleReturnsToTheCurrentAcademicYear() = runComposeUiTest {
        setContent { TwinteTheme { TestAuthenticatedApp(withCourse = true) } }
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithContentDescription("Previous year").performClick()
        onNodeWithTag("destination-Timetable").performClick()
        onNodeWithText("現在の学期").performClick()
        onNodeWithText("秋B").assertIsDisplayed()
        onNodeWithText("情報メディア実験A").assertIsDisplayed()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("drawer-year").assertTextContains("2026年度")
    }

    @Test
    fun failedRefreshKeepsCachedTilesVisibleAndCanBeRetried() = runComposeUiTest {
        val service = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = testTimetableCourses),
            refreshOperation = { error("offline") },
        )
        setContent { TwinteTheme { TestAuthenticatedApp(testSignedIn(offline = true), service = service) } }
        onNodeWithText("オフライン・保存したデータを表示").assertIsDisplayed()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithText("時間割を更新").performClick()
        onNodeWithText("情報メディア実験A").assertIsDisplayed()
        onNodeWithText("再試行").performClick()
        runOnIdle { assertEquals(2, service.refreshedAccountIds.size) }
        onNodeWithText("情報メディア実験A").assertIsDisplayed()
    }

    @Test
    fun pullingDownRefreshesTheHome() = runComposeUiTest {
        val service = FakeTimetableService()
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("timetable-grid").performTouchInput { swipeDown() }
        runOnIdle { assertEquals(1, service.refreshedAccountIds.size) }
    }

    @Test
    fun rendersHomeMenuAndSpecialCoursesInBothThemesAndSmallScreens() {
        val configurations = listOf(
            Triple(Size(390f, 844f), false, 1f),
            Triple(Size(390f, 844f), true, 1f),
            Triple(Size(320f, 640f), false, 1.3f),
            Triple(Size(844f, 390f), false, 1f),
            Triple(Size(800f, 1100f), false, 1f),
            Triple(Size(800f, 1100f), true, 1f),
        )
        configurations.forEach { (size, dark, scale) ->
            runSkikoComposeUiTest(size = size, density = Density(1f, scale)) {
                setContent { TwinteTheme(darkTheme = dark) { TestAuthenticatedApp(withCourse = true) } }
                val prefix = "${size.width.toInt()}x${size.height.toInt()}-${if (dark) "dark" else "light"}-$scale"
                onNodeWithContentDescription("Twin:te").assertIsDisplayed()
                saveImage("$prefix-home")
                onNodeWithContentDescription("学期を選択").performClick()
                onNodeWithTag("module-menu").assertIsDisplayed()
                saveImage("$prefix-module-menu")
                onNode(hasText("秋B") and hasAnyAncestor(hasTestTag("module-menu"))).performClick()
                onNodeWithContentDescription("メニュー").performClick()
                onNodeWithTag("destination-Courses").assertIsDisplayed()
                saveImage("$prefix-menu")
                onNodeWithTag("destination-Timetable").performClick()
                onNodeWithText("特殊").performClick()
                onNodeWithText("集中講義・情報学特別演習").assertIsDisplayed()
                saveImage("$prefix-special")
            }
        }
    }

    private fun SkikoComposeUiTest.saveImage(name: String) {
        val file = File("build/reports/timetable-ui/$name.png")
        file.parentFile.mkdirs()
        Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData()!!.use { data -> file.writeBytes(data.bytes) }
        }
    }
}

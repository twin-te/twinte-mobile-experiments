package net.twinte.mobile_experiments

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CompletableDeferred
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class CourseEditorUiTest {
    @Test
    fun multipleRoomsCanBeAssignedToSelectedSchedulesWithoutChangingOtherSlots() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithContentDescription("授業場所を追加").performScrollTo().performClick()
        onNodeWithTag("save-course").assertIsNotEnabled()
        onNodeWithTag("input-授業場所 2").performScrollTo().performTextInput("研究室")
        onNodeWithTag("save-course").assertIsNotEnabled()
        onNode(hasText("春A 月曜2限") and hasAnyAncestor(hasTestTag("editor-room-1")))
            .performScrollTo().performClick()
        onNodeWithTag("save-course").assertIsEnabled().performClick()
        runOnIdle {
            assertEquals(listOf("3A204,研究室", "3A204"), service.updatedInputs.single().schedules!!.map { it.locations })
        }
    }

    @Test
    fun scheduleMemoAndTagsAreSavedTogetherWithoutChangingAttendance() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithText("授業情報の編集").assertIsDisplayed()
        onNodeWithContentDescription("曜日 1").performClick()
        onNodeWithText("集中", substring = false).performScrollTo().performClick()
        onNodeWithContentDescription("時限 1").assertIsNotEnabled()
        onNodeWithTag("input-授業場所 1").performScrollTo().performTextClearance()
        onNodeWithTag("input-授業場所 1").performTextInput("研究室")
        onNodeWithContentDescription("担当教員を追加").performScrollTo().performClick()
        onNodeWithTag("input-担当教員 2").performScrollTo().performTextInput("筑波 花子")
        onNodeWithTag("input-メモ").performScrollTo().performTextClearance()
        onNodeWithTag("input-メモ").performTextInput("編集したメモ")
        onNodeWithText("専門科目").performScrollTo().performClick()
        onNodeWithTag("save-course").performClick()
        onNodeWithTag("course-detail").assertIsDisplayed()
        runOnIdle {
            val input = service.updatedInputs.single()
            val schedules = requireNotNull(input.schedules)
            assertEquals(Day.Intensive, schedules.first().day)
            assertEquals(0, schedules.first().period)
            assertEquals("研究室", schedules.first().locations)
            assertEquals("編集したメモ", input.memo)
            assertEquals("筑波 太郎,筑波 花子", input.instructors)
            assertEquals(emptyList(), input.tagIds)
            assertNull(input.attendance)
            assertNull(input.absence)
            assertNull(input.late)
        }
    }

    @Test
    fun systemBackAndHeaderBackProtectUnsavedChangesButNotAnUnmodifiedDraft() = runComposeUiTest {
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
        val input = object : NavigationEventInput() { fun back() = dispatchOnBackCompleted() }
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                TwinteTheme { TestAuthenticatedApp(service = service) }
            }
        }
        runOnIdle { owner.navigationEventDispatcher.addInput(input) }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        runOnIdle { input.back() }
        onNodeWithTag("course-detail").assertIsDisplayed()
        onNodeWithText("変更を破棄しますか？").assertDoesNotExist()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithTag("input-授業名").performTextInput(" (変更)")
        runOnIdle { input.back() }
        onNodeWithText("変更を破棄しますか？").assertIsDisplayed()
        onNodeWithText("編集を続ける").performClick()
        onNodeWithTag("input-授業名").assertTextContains("(変更)", substring = true)
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("破棄する").performClick()
        onNodeWithTag("course-detail").assertIsDisplayed()
        runOnIdle {
            assertEquals(emptyList(), service.updatedInputs)
            owner.navigationEventDispatcher.removeInput(input)
        }
    }

    @Test
    fun failedSaveRetainsTheDraftAndPendingSaveCannotBeDismissedOrSubmittedTwice() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        var fail = true
        val service = FakeTimetableService(initialSnapshot = snapshot, updateOperation = {
            pending.await()
            if (fail) error("offline")
        })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithTag("input-授業名").performTextClearance()
        onNodeWithTag("input-授業名").performTextInput("保存待ちの授業")
        onNodeWithTag("save-course").performClick()
        onNodeWithTag("save-course").assertIsNotEnabled()
        onNodeWithTag("input-授業名").assertIsNotEnabled()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithTag("course-editor").assertIsDisplayed()
        onNodeWithText("変更を破棄しますか？").assertDoesNotExist()
        runOnIdle { pending.complete(Unit) }
        onNodeWithTag("course-editor-error").assertIsDisplayed()
        onNodeWithTag("input-授業名").assertTextContains("保存待ちの授業")
        runOnIdle { fail = false }
        onNodeWithTag("save-course").performClick()
        onNodeWithText("保存待ちの授業").assertIsDisplayed()
        onNodeWithTag("course-editor").assertDoesNotExist()
        runOnIdle { assertEquals(2, service.updatedInputs.size) }
    }

    @Test
    fun duplicateScheduleDisablesSaveAndCanBeRemoved() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithContentDescription("開講時限を追加").performScrollTo().performClick()
        onNodeWithContentDescription("時限 3").performScrollTo().performClick()
        onNode(hasText("2限") and hasAnyAncestor(hasTestTag("select-menu-時限 3"))).performClick()
        onNodeWithText("同じ学期・曜日・時限が重複しています。").performScrollTo().assertIsDisplayed()
        onNodeWithTag("save-course").assertIsNotEnabled()
        onNodeWithContentDescription("時限 3 を削除").performScrollTo().performClick()
        onNodeWithTag("save-course").assertIsEnabled()
    }

    @Test
    fun manualCreationUsesTheSameFormAndValidation() = runComposeUiTest {
        val service = FakeTimetableService()
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の追加メニュー").performClick()
        onNodeWithText("手動で授業を作成").performClick()
        onNodeWithTag("save-course").assertIsNotEnabled()
        onNodeWithTag("input-授業名").performTextInput("自主ゼミ")
        onNodeWithTag("input-単位数").performScrollTo().performTextInput("1.2")
        onNodeWithTag("save-course").assertIsNotEnabled()
        onNodeWithTag("input-単位数").performTextClearance()
        onNodeWithTag("input-単位数").performTextInput("1.5")
        onNodeWithTag("save-course").performClick()
        onNodeWithTag("screen-Courses").assertIsDisplayed()
        runOnIdle { assertEquals("自主ゼミ", service.createdManualInputs.single().name) }
    }

    @Test
    fun editorFitsBothThemesLandscapeTabletAndLargeText() {
        listOf(
            Triple(Size(390f, 844f), false, 1f),
            Triple(Size(390f, 844f), true, 1f),
            Triple(Size(320f, 640f), false, 2f),
            Triple(Size(844f, 390f), false, 1f),
            Triple(Size(800f, 1100f), false, 1f),
        ).forEach { (size, dark, scale) ->
            runSkikoComposeUiTest(size = size, density = Density(1f, scale)) {
                val service = FakeTimetableService(initialSnapshot = snapshot)
                setContent { TwinteTheme(darkTheme = dark) { TestAuthenticatedApp(service = service) } }
                onNodeWithTag("slot-Monday-2").performScrollTo().performClick()
                onNodeWithContentDescription("授業の操作").performClick()
                onNodeWithText("編集する").performScrollTo().performClick()
                onNodeWithText("授業情報の編集").assertIsDisplayed()
                onNodeWithTag("input-授業名").assertIsDisplayed()
                onNodeWithTag("save-course").assertIsDisplayed()
                val prefix = "${size.width.toInt()}x${size.height.toInt()}-$dark-$scale"
                fun saveImage(suffix: String) {
                    val file = File("build/reports/course-editor-ui/$prefix-$suffix.png")
                    file.parentFile.mkdirs()
                    Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { file.writeBytes(it.bytes) }
                    }
                }
                saveImage("top")
                onNodeWithContentDescription("学期 1").performScrollTo().performClick()
                onNodeWithText("春休み", substring = false).performScrollTo().assertIsDisplayed()
                saveImage("dropdown")
                onNodeWithText("春休み", substring = false).performClick()
                onNodeWithTag("input-メモ").performScrollTo().assertIsDisplayed()
                onNodeWithText("専門科目").performScrollTo().assertIsDisplayed()
                saveImage("bottom")
            }
        }
    }

    private val snapshot = TimetableSnapshot(
        courses = listOf(testTimetableCourses.first().copy(memo = "持ち物を確認", tagIds = listOf("tag"))),
        tags = listOf(Tag("tag", "専門科目", 0)),
    )
}

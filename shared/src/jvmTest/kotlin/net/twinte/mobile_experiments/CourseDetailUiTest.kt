package net.twinte.mobile_experiments

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.timetable.TimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CourseDetailUiTest {
    @Test
    fun specialCourseReturnsToTheSpecialTimetable() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = testTimetableCourses))
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithText("特殊").performClick()
        onNodeWithText("集中講義・情報学特別演習").performClick()
        onNodeWithText("夏休み 集中").assertIsDisplayed()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("集中講義・情報学特別演習").assertIsDisplayed()
    }

    @Test
    fun savingTheExistingEditorUpdatesTheDetailsAndTheHomeTile() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithTag("editor-授業名").performTextClearance()
        onNodeWithTag("editor-授業名").performTextInput("Updated course")
        onNodeWithTag("save-course").performClick()
        onNodeWithText("Updated course").assertIsDisplayed()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Updated course").assertIsDisplayed()
    }

    @Test
    fun singleCourseOpensFromCacheAndReturnsHomeWithoutFetching() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithTag("course-selection").assertDoesNotExist()
        onNodeWithTag("course-detail").assertIsDisplayed()
        onNodeWithText("GB12345").assertIsDisplayed()
        onNodeWithText("春A・秋B 月曜2限").assertIsDisplayed()
        onNodeWithText("対面").assertIsDisplayed()
        onNodeWithText("専門科目").performScrollTo().assertIsDisplayed()
        onNodeWithText("レポート提出は金曜日").performScrollTo().assertIsDisplayed()
        runOnIdle { assertEquals(emptyList(), service.refreshedAccountIds) }
        onNodeWithContentDescription("Back").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        onNodeWithText("秋B").assertIsDisplayed()
    }

    @Test
    fun attendanceWaitsForTheServerAndAppliesOnlyTheChangedCounter() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        val service = FakeTimetableService(initialSnapshot = snapshot, updateOperation = { pending.await() })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("出席を減らす").performScrollTo().assertIsNotEnabled()
        onNodeWithContentDescription("出席を増やす").performClick()
        onNodeWithTag("counter-Attendance").assertTextEquals("0")
        onNodeWithContentDescription("出席を増やす").assertIsNotEnabled()
        onNodeWithContentDescription("欠席を増やす").assertIsNotEnabled()
        runOnIdle { pending.complete(Unit) }
        onNodeWithTag("counter-Attendance").assertTextEquals("1")
        onNodeWithTag("counter-Absence").assertTextEquals("0")
        onNodeWithContentDescription("出席を減らす").assertIsEnabled()
        runOnIdle {
            assertEquals(1, service.updatedInputs.single().attendance)
            assertEquals(null, service.updatedInputs.single().absence)
        }
    }

    @Test
    fun failedAttendanceKeepsTheCachedValueAndCanBeRetried() = runComposeUiTest {
        var fail = true
        val service = FakeTimetableService(initialSnapshot = snapshot, updateOperation = { if (fail) error("offline") })
        setContent { TwinteTheme { TestAuthenticatedApp(testSignedIn(offline = true), service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("出席を増やす").performScrollTo().performClick()
        onNodeWithTag("attendance-error").performScrollTo().assertIsDisplayed()
        onNodeWithTag("counter-Attendance").assertTextEquals("0")
        runOnIdle { fail = false }
        onNodeWithContentDescription("出席を増やす").performScrollTo().performClick()
        onNodeWithTag("counter-Attendance").assertTextEquals("1")
        onNodeWithTag("attendance-error").assertDoesNotExist()
    }

    @Test
    fun deletionConfirmsAndRetainsDetailsOnFailureThenReturnsHomeOnSuccess() = runComposeUiTest {
        var fail = true
        val service = FakeTimetableService(initialSnapshot = snapshot, deleteOperation = { if (fail) error("offline") })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("削除する").performClick()
        onNodeWithText("キャンセル").performClick()
        onNodeWithTag("course-detail").assertIsDisplayed()
        runOnIdle { assertEquals(emptyList(), service.deletedCourseIds) }
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("削除する").performClick()
        onNodeWithText("削除", substring = false).performClick()
        onNodeWithText("削除できませんでした。再度お試しください。").assertIsDisplayed()
        onNodeWithTag("course-detail").assertExists()
        runOnIdle { fail = false }
        onNodeWithText("削除", substring = false).performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        onNodeWithTag("course-detail").assertDoesNotExist()
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithTag("screen-Courses").assertIsDisplayed()
    }

    @Test
    fun routeAndEditDraftRestoreWhileWaitingForTheCache() = runComposeUiTest {
        val restoration = SaveableComposition()
        val source = FakeTimetableService(initialSnapshot = snapshot)
        var gate: CompletableDeferred<Unit>? = null
        val service = object : TimetableService by source {
            override fun observeTimetable(accountId: String, year: Int?) = flow {
                gate?.await()
                emitAll(source.observeTimetable(accountId, year))
            }
        }
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(service = service) } } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithTag("editor-授業名").performTextClearance()
        onNodeWithTag("editor-授業名").performTextInput("Unsaved course")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle {
            gate = CompletableDeferred()
            restoration.restore()
        }
        onNodeWithTag("course-editor-empty").assertIsDisplayed()
        onNodeWithText("授業が見つかりません").assertDoesNotExist()
        runOnIdle { gate!!.complete(Unit) }
        onNodeWithText("Unsaved course").assertExists()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("破棄する").performClick()
        onNodeWithTag("course-detail").assertIsDisplayed()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
    }

    @Test
    fun aRemovedCourseOffersBackInsteadOfShowingStaleDetails() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        runOnIdle { service.emitSnapshot(TimetableSnapshot()) }
        onNodeWithText("授業が見つかりません").assertIsDisplayed()
        onNodeWithContentDescription("授業の操作").assertDoesNotExist()
        onNodeWithText("戻る").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
    }

    @Test
    fun logoutAndAccountSwitchDiscardTheDetailRoute() = runComposeUiTest {
        val auth = mutableStateOf(testSignedIn())
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent {
            TwinteTheme {
                AuthenticationGate(auth.value, true, false, {}, {}) {
                    TestAuthenticatedApp(auth.value, service = service)
                }
            }
        }
        onNodeWithTag("slot-Monday-2").performClick()
        runOnIdle { auth.value = testSignedIn("other-user") }
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        onNodeWithTag("course-detail").assertDoesNotExist()
        runOnIdle { auth.value = AuthUiState(AuthState.LoggedOut, "Signed out") }
        onNodeWithText("Google").assertIsDisplayed()
    }

    @Test
    fun dismissingDetailsAlsoRemovesRoutesFromHiddenTabs() = runComposeUiTest {
        lateinit var navigator: AppNavigator
        setContent {
            navigator = rememberAppNavigator()
            Text(navigator.currentRoute.toString())
        }
        runOnIdle {
            navigator.openCourseDetail("first")
            navigator.openCourseDetail("first")
            assertEquals(2, navigator.stacks.getValue(AppTab.Timetable).size)
            navigator.selectTab(AppTab.Courses)
            navigator.openCourseDetail("second")
            navigator.dismissCourseScreens("first")
            assertEquals(AppRoute.CourseDetail("second"), navigator.currentRoute)
            assertEquals(listOf(AppRoute.Timetable), navigator.stacks.getValue(AppTab.Timetable).toList())
            navigator.dismissCourseScreens()
            assertEquals(AppRoute.Courses, navigator.currentRoute)
        }
    }

    @Test
    fun rendersDetailsInBothThemesAndAtLargeFontSizes() {
        listOf(
            Triple(Size(390f, 844f), false, 1f),
            Triple(Size(390f, 844f), true, 1f),
            Triple(Size(320f, 640f), false, 2f),
            Triple(Size(800f, 1100f), false, 1f),
        ).forEach { (size, dark, scale) ->
            runSkikoComposeUiTest(size = size, density = Density(1f, scale)) {
                val service = FakeTimetableService(initialSnapshot = snapshot)
                setContent {
                    TwinteTheme(darkTheme = dark) {
                        TestAuthenticatedApp(service = service)
                    }
                }
                onNodeWithTag("slot-Monday-2").performScrollTo().performClick()
                onNodeWithText("担当教員").performScrollTo().assertIsDisplayed()
                val prefix = "${size.width.toInt()}x${size.height.toInt()}-${if (dark) "dark" else "light"}-$scale"
                fun saveImage(suffix: String) {
                    val file = File("build/reports/course-detail-ui/$prefix-$suffix.png")
                    file.parentFile.mkdirs()
                    Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { file.writeBytes(it.bytes) }
                    }
                }
                saveImage("details")
                onNodeWithContentDescription("出席を増やす").performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription("遅刻を増やす").performScrollTo().assertIsDisplayed()
                saveImage("attendance")
            }
        }
    }
}

private val snapshot = TimetableSnapshot(
    courses = listOf(testTimetableCourses.first().copy(
        code = "GB12345", memo = "レポート提出は金曜日", tagIds = listOf("specialty"),
    )),
    tags = listOf(Tag("specialty", "専門科目", 0), Tag("optional", "復習", 1)),
)

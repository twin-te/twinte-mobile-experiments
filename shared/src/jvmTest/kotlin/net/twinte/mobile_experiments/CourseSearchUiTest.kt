package net.twinte.mobile_experiments

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.timetable.TimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CourseSearchUiTest {
    @Test
    fun simpleModeExpandsIntoDetailsAndOpensTheSyllabusWithoutLosingSelection() = runComposeUiTest {
        val service = FakeTimetableService(searchResults = listOf(searchTestCourse("GB1")))
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        search()
        select("GB1")
        onNodeWithText("簡易", substring = false).performClick()
        onNodeWithText("1.0 単位").assertDoesNotExist()
        onNodeWithTag("search-result-GB1").performClick()
        onNodeWithText("1.0 単位").assertIsDisplayed()
        onNodeWithText("履修上の注意事項").assertIsDisplayed()
        onNodeWithText("シラバス", substring = false).performClick()
        onNodeWithTag("browser-sheet").assertIsDisplayed()
        onNodeWithText("kdb.tsukuba.ac.jp").assertIsDisplayed()
        onNodeWithContentDescription("閉じる").performClick()
        onNodeWithText("選択中の授業: 1件").assertIsDisplayed()
    }

    @Test
    fun blankOnlySearchUsesTheCachedTimetableAndHidesManualConditions() = runComposeUiTest {
        val existing = testTimetableCourses.first()
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(existing)))
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        onNodeWithTag("search-conditions").performClick()
        onNodeWithText("空いているコマのみを検索").performClick()
        onNodeWithContentDescription("検索学期 1").assertDoesNotExist()
        search()
        runOnIdle {
            val input = requireNotNull(service.lastSearchInput)
            assertEquals(emptyList(), input.schedulesPartiallyOverlapped)
            kotlin.test.assertTrue(input.schedulesFullyIncluded.isNotEmpty())
            kotlin.test.assertTrue(input.schedulesFullyIncluded.none { it.module == Module.FallB && it.day == Day.Monday && it.period == 2 })
        }
    }

    @Test
    fun emptySlotOpensSearchAndHeaderBackReturnsToTheSameModule() = runComposeUiTest {
        setContent { TwinteTheme { TestAuthenticatedApp() } }
        openSearch()
        onNodeWithText("授業の検索").assertIsDisplayed()
        onNodeWithTag("input-キーワード").performTextInput("情報")
        onNodeWithContentDescription("Back").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        onNodeWithText("秋B").assertIsDisplayed()
        openSearch()
        onNodeWithTag("input-キーワード").assertTextContains("情報")
    }

    @Test
    fun filtersAndKeyboardSearchUseTheExistingStructuredApi() = runComposeUiTest {
        val service = FakeTimetableService()
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        onNodeWithTag("input-科目番号").performTextInput("GB -0")
        onNodeWithTag("input-キーワード").performTextInput("情報　倫理")
        onNodeWithTag("search-conditions").performClick()
        onNodeWithContentDescription("検索学期 1").performScrollTo().performClick()
        onNodeWithText("春B", substring = false).performClick()
        onNodeWithContentDescription("検索曜日 1").performScrollTo().performClick()
        onNodeWithText("月曜", substring = false).performClick()
        onNodeWithContentDescription("検索時限 1").performScrollTo().performClick()
        onNodeWithText("2限", substring = false).performClick()
        onNodeWithTag("input-キーワード").performImeAction()
        onNodeWithText("一致する授業がありません。").assertIsDisplayed()
        runOnIdle {
            val input = requireNotNull(service.lastSearchInput)
            assertEquals(listOf("GB"), input.codePrefixesIncluded)
            assertEquals(listOf("0"), input.codePrefixesExcluded)
            assertEquals(listOf("情報", "倫理"), input.keywords)
            assertEquals(Module.SpringB, input.schedulesPartiallyOverlapped.single().module)
            assertEquals(Day.Monday, input.schedulesPartiallyOverlapped.single().day)
            assertEquals(2, input.schedulesPartiallyOverlapped.single().period)
        }
    }

    @Test
    fun selectionsSurviveNewSearchesAndAreRegisteredInOneRequestAfterConflictConfirmation() = runComposeUiTest {
        val first = searchTestCourse("GB1")
        val second = searchTestCourse("GB2")
        val service = FakeTimetableService(searchResultProvider = { input -> if (input.keywords.isEmpty()) listOf(first) else listOf(second) })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        search()
        select("GB1")
        onNodeWithTag("input-キーワード").performTextInput("次の検索")
        search()
        select("GB2")
        onNodeWithText("選択中の授業: 2件").assertIsDisplayed()
        onNodeWithTag("register-courses").performClick()
        onNodeWithText("開講時限が重複しています").assertIsDisplayed()
        onNodeWithText("キャンセル").performClick()
        runOnIdle { assertEquals(emptyList(), service.registrationRequests) }
        onNodeWithTag("register-courses").performClick()
        onNodeWithText("そのまま追加").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        runOnIdle { assertEquals(listOf(2026 to listOf("GB1", "GB2")), service.registrationRequests) }
        openSearch()
        onNodeWithText("選択中の授業: 0件").assertIsDisplayed()
        onNodeWithTag("register-courses").assertIsNotEnabled()
    }

    @Test
    fun registrationFailureKeepsSelectionsAndCanBeRetriedWithoutReselecting() = runComposeUiTest {
        var fail = true
        val service = FakeTimetableService(searchResults = listOf(searchTestCourse("GB1")), registerOperation = { if (fail) error("offline") })
        setContent { TwinteTheme { TestAuthenticatedApp(testSignedIn(offline = true), service = service) } }
        openSearch()
        search()
        select("GB1")
        onNodeWithTag("register-courses").performClick()
        onNodeWithTag("registration-error").assertIsDisplayed()
        onNodeWithText("選択中の授業: 1件").assertIsDisplayed()
        onNodeWithTag("register-courses").assertIsEnabled()
        runOnIdle { fail = false }
        onNodeWithTag("register-courses").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        runOnIdle { assertEquals(2, service.registrationRequests.size) }
    }

    @Test
    fun failedSearchRetainsThePreviousResultsAndRetryUsesItsOriginalQuery() = runComposeUiTest {
        var fail = false
        val service = FakeTimetableService(searchResultProvider = {
            if (fail) error("offline")
            listOf(searchTestCourse("GB1"))
        })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        search()
        select("GB1")
        runOnIdle { fail = true }
        onNodeWithTag("input-キーワード").performTextInput("失敗した検索")
        search()
        onNodeWithTag("search-error").assertIsDisplayed()
        onNodeWithTag("search-result-GB1").assertExists()
        onNodeWithText("選択中の授業: 1件").assertIsDisplayed()
        onNodeWithTag("input-キーワード").performTextClearance()
        onNodeWithTag("input-キーワード").performTextInput("まだ検索しない")
        runOnIdle { fail = false }
        onNodeWithText("再試行").performClick()
        runOnIdle { assertEquals(listOf("失敗した検索"), service.lastSearchInput!!.keywords) }
        onNodeWithTag("search-error").assertDoesNotExist()
    }

    @Test
    fun pendingRegistrationLocksSelectionAndSubmissionAndDoesNotRedirectAfterAYearChange() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        val service = FakeTimetableService(searchResults = listOf(searchTestCourse("GB1")), registerOperation = { pending.await() })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        search()
        select("GB1")
        onNodeWithTag("register-courses").performClick()
        onNodeWithTag("register-courses").assertIsNotEnabled()
        onNodeWithContentDescription("GB1 を選択").assertIsNotEnabled()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithContentDescription("Previous year").performClick()
        onNodeWithTag("destination-Courses").performClick()
        runOnIdle { pending.complete(Unit) }
        onNodeWithTag("screen-Courses").assertIsDisplayed()
        onNodeWithTag("search-year").assertTextContains("2025年度")
        onNodeWithText("選択中の授業: 0件").assertIsDisplayed()
        runOnIdle { assertEquals(listOf(2026 to listOf("GB1")), service.registrationRequests) }
    }

    @Test
    fun restoredSelectionWaitsForCacheThenPrunesCoursesAlreadyRegisteredElsewhere() = runComposeUiTest {
        val restoration = SaveableComposition()
        val source = FakeTimetableService(searchResults = listOf(searchTestCourse("GB1")))
        var gate: CompletableDeferred<Unit>? = null
        val service = object : TimetableService by source {
            override fun observeTimetable(accountId: String, year: Int?) = flow {
                gate?.await()
                emitAll(source.observeTimetable(accountId, year))
            }
        }
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(service = service) } } }
        openSearch()
        search()
        select("GB1")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle {
            source.emitSnapshot(TimetableSnapshot(courses = listOf(testTimetableCourses.first().copy(code = "GB1"))))
            gate = CompletableDeferred()
            restoration.restore()
        }
        onNodeWithText("選択中の授業: 1件").assertIsDisplayed()
        onNodeWithTag("register-courses").assertIsNotEnabled()
        runOnIdle { gate!!.complete(Unit) }
        onNodeWithText("選択中の授業: 0件").assertIsDisplayed()
        onNodeWithTag("register-courses").assertIsNotEnabled()
    }

    @Test
    fun selectedBasketConfirmsRemovalAndRegisteredResultsCannotBeSelectedAgain() = runComposeUiTest {
        val existing = testTimetableCourses.first().copy(code = "EXISTING")
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = listOf(existing)),
            searchResults = listOf(searchTestCourse("EXISTING"), searchTestCourse("NEW")))
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        search()
        onNodeWithContentDescription("EXISTING を選択").assertIsNotEnabled()
        select("NEW")
        onNodeWithTag("selected-courses-toggle").performClick()
        onNodeWithContentDescription("NEW の選択を解除").performClick()
        onNodeWithText("キャンセル").performClick()
        onNodeWithText("選択中の授業: 1件").assertIsDisplayed()
        onNodeWithContentDescription("NEW の選択を解除").performClick()
        onNodeWithText("解除", substring = false).performClick()
        onNodeWithText("選択中の授業: 0件").assertIsDisplayed()
    }

    @Test
    fun scrollingLoadsAnotherPageWithoutDroppingSelections() = runComposeUiTest {
        val service = FakeTimetableService(searchResultProvider = { input ->
            if (input.offset == 0) (0..49).map { searchTestCourse("GB$it") } else listOf(searchTestCourse("GB50"))
        })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        openSearch()
        search()
        select("GB0")
        onNodeWithTag("course-search-list").performScrollToNode(hasTestTag("search-result-GB45"))
        onNodeWithTag("course-search-list").performScrollToNode(hasTestTag("search-result-GB50"))
        select("GB50")
        onNodeWithText("選択中の授業: 2件").assertIsDisplayed()
        runOnIdle { assertEquals(listOf(0, 50), service.searchInputs.map { it.offset }) }
    }

    @Test
    fun rendersSearchResultsConditionsAndSelectionInBothThemesAndResponsiveSizes() {
        listOf(
            Triple(Size(390f, 844f), false, 1f), Triple(Size(390f, 844f), true, 1f),
            Triple(Size(320f, 640f), false, 2f), Triple(Size(844f, 390f), false, 1f),
            Triple(Size(800f, 1100f), false, 1f),
        ).forEach { (size, dark, scale) ->
            runSkikoComposeUiTest(size = size, density = Density(1f, scale)) {
                val service = FakeTimetableService(searchResults = listOf(searchTestCourse("GB12345"), searchTestCourse("GB98765", day = Day.Tuesday)))
                setContent { TwinteTheme(darkTheme = dark) { TestAuthenticatedApp(service = service) } }
                openSearch()
                onNodeWithTag("register-courses").assertIsDisplayed()
                val prefix = "${size.width.toInt()}x${size.height.toInt()}-$dark-$scale"
                fun saveImage(suffix: String) {
                    val file = File("build/reports/course-search-ui/$prefix-$suffix.png")
                    file.parentFile.mkdirs()
                    Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { file.writeBytes(it.bytes) }
                    }
                }
                saveImage("initial")
                search()
                onNodeWithTag("course-search-list").performScrollToNode(hasTestTag("search-result-GB12345"))
                saveImage("results")
                select("GB12345")
                onNodeWithTag("selected-courses-toggle").performClick()
                saveImage("selection")
                onNodeWithTag("selected-courses-toggle").performClick()
                onNodeWithTag("course-search-list").performScrollToNode(hasTestTag("search-conditions"))
                onNodeWithTag("search-conditions").performClick()
                onNodeWithTag("course-search-list").performScrollToNode(hasContentDescription("検索学期 1"))
                saveImage("conditions")
            }
        }
    }

    private fun ComposeUiTest.openSearch() {
        onNodeWithTag("slot-Friday-1").performScrollTo().performClick()
    }

    private fun ComposeUiTest.search() {
        onNodeWithTag("course-search-list").performScrollToNode(hasContentDescription("検索"))
        onNodeWithContentDescription("検索").performClick()
    }

    private fun ComposeUiTest.select(code: String) {
        onNodeWithTag("course-search-list").performScrollToNode(hasContentDescription("$code を選択"))
        onNodeWithContentDescription("$code を選択").performClick()
    }
}

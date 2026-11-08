package net.twinte.mobile_experiments

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.timetable.DailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class TimetableInteractionUiTest {
    @Test
    fun pendingSearchDoesNotOpenALockedEditorFromHome() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        val service = FakeTimetableService(
            initialSnapshot = TimetableSnapshot(courses = testTimetableCourses),
            searchResultProvider = {
                pending.await()
                emptyList()
            },
        )
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-Courses").performClick()
        onNodeWithText("Search", substring = false).performClick()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-Timetable").performClick()
        onNodeWithTag("slot-Monday-2").performClick()
        onNode(hasText("情報メディア実験A") and hasAnyAncestor(hasTestTag("course-selection"))).performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").assertIsNotEnabled()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        onNodeWithTag("course-detail").assertDoesNotExist()

        onNodeWithTag("slot-Monday-2").performClick()
        onNode(hasText("情報メディア実験A") and hasAnyAncestor(hasTestTag("course-selection"))).performClick()
        runOnIdle { pending.complete(Unit) }
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").assertIsEnabled().performClick()
        onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        onNodeWithContentDescription("Back").performClick()
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
    }

    @Test
    fun emptySlotReturnsFromTagsToSearchAtTheTopWithoutDiscardingTheQuery() = runComposeUiTest {
        setContent { TwinteTheme { TestAuthenticatedApp(withCourse = true) } }
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Retained query")
        onNodeWithText("Manage tags").performScrollTo().performClick()
        onNodeWithText("New tag").performTextInput("Tag draft")
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-Timetable").performClick()

        onNodeWithTag("slot-Monday-1").performClick()
        onNodeWithTag("screen-Courses").assertIsDisplayed()
        onNodeWithText("Name or keyword").assertIsDisplayed().assertTextContains("Retained query")
        onNodeWithText("Search", substring = false).assertIsDisplayed()
        onNodeWithText("New tag").assertDoesNotExist()
    }

    @Test
    fun sharedEditorRestoresAnUnsavedDraftOpenedFromCourses() = runComposeUiTest {
        val restoration = SaveableComposition()
        val service = FakeTimetableService(initialSnapshot = TimetableSnapshot(courses = testTimetableCourses.take(1)))
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(service = service) } } }
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-Courses").performClick()
        onNodeWithText("Edit", substring = false).performScrollTo().performClick()
        onNodeWithTag("input-授業名").performTextClearance()
        onNodeWithTag("input-授業名").performTextInput("Unsaved draft")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }
        onNodeWithTag("input-授業名").assertTextContains("Unsaved draft")
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("破棄する").performClick()
        onNodeWithTag("screen-Courses").assertIsDisplayed()

        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-Timetable").performClick()
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithContentDescription("授業の操作").performClick()
        onNodeWithText("編集する").performClick()
        onNodeWithTag("input-授業名").assertTextContains("情報メディア実験A")
        onNodeWithContentDescription("Back").performClick()
    }

    @Test
    fun restoringBeforeCalendarArrivesStillInitializesTheCurrentModule() = runComposeUiTest {
        val restoration = SaveableComposition()
        val daily = FakeDailyTimetableService()
        val date = LocalDate(2026, 11, 6)
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(dailyService = daily, date = date) } } }
        onNodeWithText("春A").assertIsDisplayed()
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }
        runOnIdle { daily.emitSnapshot(snapshot(date, Module.FallB)) }
        onNodeWithText("秋B").assertIsDisplayed()
    }

    @Test
    fun vacationCurrentModuleRemainsAvailableAndReturnsToTheAcademicYear() = runComposeUiTest {
        val date = LocalDate(2027, 1, 3)
        val daily = FakeDailyTimetableService(initialSnapshot = snapshot(date, Module.WinterVacation))
        setContent { TwinteTheme { TestAuthenticatedApp(dailyService = daily, date = date) } }
        onNodeWithText("秋C").assertIsDisplayed()
        onNodeWithText("現在の学期").assertIsEnabled()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithContentDescription("Previous year").performClick()
        onNodeWithTag("drawer-year").assertTextContains("2025年度")
        onNodeWithTag("destination-Timetable").performClick()
        onNodeWithText("現在の学期").performClick()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("drawer-year").assertTextContains("2026年度")
    }

    private fun snapshot(date: LocalDate, module: Module) = DailyTimetableSnapshot(
        DailyTimetable(date, module, Day.Friday, "通常日課", emptyList()),
        Instant.fromEpochMilliseconds(0),
    )
}

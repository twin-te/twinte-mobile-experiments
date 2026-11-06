package net.twinte.mobile_experiments

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.timetable.DailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class TimetableInteractionUiTest {
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

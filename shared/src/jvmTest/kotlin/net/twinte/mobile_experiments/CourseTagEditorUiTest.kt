package net.twinte.mobile_experiments

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CourseTagEditorUiTest {
    @Test
    fun showsAssignedAndUnassignedTagsAndWaitsForTheServerBeforeToggling() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        val service = FakeTimetableService(initialSnapshot = snapshot, updateOperation = { pending.await() })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithTag("course-tag-first").performScrollTo().assertIsOn()
        onNodeWithTag("course-tag-second").assertIsOff().performClick()
        onNodeWithTag("course-tag-second").assertIsOff().assertIsNotEnabled()
        onNodeWithTag("course-tag-progress").assertIsDisplayed()
        runOnIdle { pending.complete(Unit) }
        onNodeWithTag("course-tag-second").assertIsOn().assertIsEnabled()
        onNodeWithTag("course-tag-progress").assertDoesNotExist()
        onNodeWithTag("course-tag-first").assertIsOn().performClick()
        onNodeWithTag("course-tag-first").assertIsOff()
        runOnIdle { assertEquals(listOf("second"), service.updatedInputs.last().tagIds) }
    }

    @Test
    fun createsAndAssignsATagWithoutDiscardingTheOtherAssignedTags() = runComposeUiTest {
        val service = FakeTimetableService(initialSnapshot = snapshot)
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithText("タグを新たに作成する").performScrollTo().performClick()
        onNodeWithContentDescription("タグを作成").assertIsNotEnabled()
        onNodeWithContentDescription("タグ名").performTextInput("  試験対策  ")
        onNodeWithContentDescription("タグを作成").performClick()
        onNodeWithTag("course-tag-created").performScrollTo().assertIsOn()
        onNodeWithTag("course-tag-first").assertIsOn()
        onNodeWithContentDescription("タグ名").assertDoesNotExist()
        runOnIdle {
            assertEquals(listOf("試験対策"), service.createdTagNames)
            assertEquals(listOf("first", "created"), service.updatedInputs.single().tagIds)
        }
    }

    @Test
    fun anAssignmentFailureCanBeRetriedWithoutCreatingAnotherTag() = runComposeUiTest {
        var fail = true
        val service = FakeTimetableService(initialSnapshot = snapshot, updateOperation = { if (fail) error("offline") })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithText("タグを新たに作成する").performScrollTo().performClick()
        onNodeWithContentDescription("タグ名").performTextInput("再試行するタグ")
        onNodeWithContentDescription("タグを作成").performClick()
        onNodeWithTag("course-tag-error").performScrollTo().assertIsDisplayed()
        onNodeWithTag("course-tag-created").assertIsOff()
        onNodeWithTag("course-tag-first").assertIsOn()
        runOnIdle { fail = false }
        onNodeWithTag("course-tag-created").performScrollTo().performClick()
        onNodeWithTag("course-tag-created").assertIsOn()
        onNodeWithTag("course-tag-error").assertDoesNotExist()
        runOnIdle { assertEquals(1, service.createdTagNames.size) }
    }

    @Test
    fun failedCreationRetainsTheRestoredDraftAndCanBeRetried() = runComposeUiTest {
        val restoration = SaveableComposition()
        var fail = true
        val service = FakeTimetableService(initialSnapshot = snapshot, createTagOperation = { if (fail) error("offline") })
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp(service = service) } } }
        onNodeWithTag("slot-Monday-2").performClick()
        onNodeWithText("タグを新たに作成する").performScrollTo().performClick()
        onNodeWithContentDescription("タグ名").performTextInput("未保存のタグ")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }
        onNodeWithContentDescription("タグ名").assertTextContains("未保存のタグ")
        onNodeWithContentDescription("タグを作成").performScrollTo().performClick()
        onNodeWithTag("course-tag-error").performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription("タグ名").assertTextContains("未保存のタグ")
        runOnIdle { fail = false }
        onNodeWithContentDescription("タグを作成").performClick()
        onNodeWithTag("course-tag-created").performScrollTo().assertIsOn()
    }
}

private val snapshot = TimetableSnapshot(
    courses = listOf(testTimetableCourses.first().copy(tagIds = listOf("first"))),
    tags = listOf(Tag("first", "専門科目", 0), Tag("second", "復習", 1)),
)

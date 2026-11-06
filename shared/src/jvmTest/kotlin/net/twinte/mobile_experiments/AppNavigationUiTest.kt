package net.twinte.mobile_experiments

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CompletableDeferred
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.ui.theme.TwinteTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppNavigationUiTest {
    @Test
    fun tabsAndNestedBackNavigationRetainSearchInput() = runComposeUiTest {
        setContent { TwinteTheme { TestAuthenticatedApp() } }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Seminar")
        navigate(AppTab.Account)
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").assertTextContains("Seminar")

        onNodeWithText("Manage tags").performScrollTo().performClick()
        onNodeWithText("New tag").performTextInput("Important")
        navigate(AppTab.Today)
        navigate(AppTab.Courses)
        onNodeWithText("New tag").assertTextContains("Important")
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")
    }

    @Test
    fun recreationRestoresTabNestedRouteAndInputs() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp() } } }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Seminar")
        onNodeWithText("Manage tags").performScrollTo().performClick()
        onNodeWithText("New tag").performTextInput("Important")

        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }

        onNodeWithTag("screen-Tags").assertIsDisplayed()
        onNodeWithText("New tag").assertTextContains("Important")
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")
    }

    @Test
    fun recreationPreservesUnsavedCourseDraft() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp() } } }
        navigate(AppTab.Courses)
        onNodeWithText("Create course").performScrollTo().performClick()
        onNodeWithText("Name", substring = false).performTextInput("New seminar")
        onNodeWithText("Credit (e.g. 1.0)").performTextInput("1.5")

        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }

        onNodeWithText("Name", substring = false).assertTextContains("New seminar")
        onNodeWithText("Credit (e.g. 1.0)").assertTextContains("1.5")
        onNodeWithText("Cancel").performClick()
        onNodeWithTag("screen-Courses").assertIsDisplayed()
    }

    @Test
    fun logoutRemovesProtectedScreensAndLoginStartsAtHome() = runComposeUiTest {
        var auth by mutableStateOf(testSignedIn())
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) {
                    TestAuthenticatedApp(auth)
                }
            }
        }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Private query")
        runOnIdle { auth = AuthUiState(AuthState.LoggedOut, "Signed out") }
        onNodeWithText("Google").assertIsDisplayed()
        onNodeWithTag("screen-Courses").assertDoesNotExist()

        runOnIdle { auth = testSignedIn() }
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        navigate(AppTab.Courses)
        onNodeWithText("Private query").assertDoesNotExist()
    }

    @Test
    fun switchingAccountsResetsNavigationAndPrivateInputs() = runComposeUiTest {
        var auth by mutableStateOf(testSignedIn())
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) {
                    TestAuthenticatedApp(auth)
                }
            }
        }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Private query")
        runOnIdle { auth = testSignedIn("other-user") }

        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        navigate(AppTab.Courses)
        onNodeWithText("Private query").assertDoesNotExist()
    }

    @Test
    fun savedNavigationIsNotRestoredForADifferentAccount() = runComposeUiTest {
        var auth by mutableStateOf(testSignedIn())
        val restoration = SaveableComposition()
        setContent {
            restoration.Content {
                TwinteTheme {
                    AuthenticationGate(auth, true, false, {}, {}) { TestAuthenticatedApp(auth) }
                }
            }
        }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Private query")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle {
            auth = testSignedIn("other-user")
            restoration.restore()
        }
        onNodeWithTag("screen-Timetable").assertIsDisplayed()
        navigate(AppTab.Courses)
        onNodeWithText("Private query").assertDoesNotExist()
    }

    @Test
    fun refreshingTheSameAccountPreservesCurrentRoute() = runComposeUiTest {
        var auth by mutableStateOf(testSignedIn())
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) { TestAuthenticatedApp(auth) }
            }
        }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Seminar")
        runOnIdle { auth = testSignedIn().copy(message = "Checked session") }
        onNodeWithTag("screen-Courses").assertIsDisplayed()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")
    }

    @Test
    fun changingYearWhileCoursesAreHiddenResetsTheirSavedInputs() = runComposeUiTest {
        setContent { TwinteTheme { TestAuthenticatedApp() } }
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("Previous year query")
        navigate(AppTab.Timetable)
        previousYear()
        navigate(AppTab.Courses)
        onNodeWithTag("selected-year").assertTextContains("2025年度")
        onNodeWithText("Previous year query").assertDoesNotExist()
    }

    @Test
    fun switchingTabsDoesNotCancelAPendingCourseSearch() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        val service = FakeTimetableService(searchResultProvider = {
            pending.await()
            emptyList()
        })
        setContent { TwinteTheme { TestAuthenticatedApp(service = service) } }
        navigate(AppTab.Courses)
        onNodeWithText("Search", substring = false).performClick()
        onNodeWithText("Searching courses...").assertIsDisplayed()
        navigate(AppTab.Today)
        runOnIdle { pending.complete(Unit) }
        navigate(AppTab.Courses)
        onNodeWithText("No courses found").assertIsDisplayed()
    }

    @Test
    fun leavingTheAuthenticatedGraphCancelsAPendingCourseMutation() = runComposeUiTest {
        var auth by mutableStateOf(testSignedIn())
        val pending = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        val service = FakeTimetableService(manualCreateOperation = {
            try {
                pending.await()
            } finally {
                canceled.complete(Unit)
            }
        })
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) {
                    TestAuthenticatedApp(auth, service = service)
                }
            }
        }
        navigate(AppTab.Courses)
        onNodeWithText("Create course").performScrollTo().performClick()
        onNodeWithText("Name", substring = false).performTextInput("New seminar")
        onNodeWithText("Credit (e.g. 1.0)").performTextInput("1.5")
        onNodeWithText("Save").performClick()
        runOnIdle {
            assertEquals(1, service.createdManualInputs.size)
            auth = AuthUiState(AuthState.LoggedOut, "Session expired")
        }
        onNodeWithText("Session expired").assertIsDisplayed()
        runOnIdle { assertTrue(canceled.isCompleted) }
        onNodeWithTag("screen-Courses").assertDoesNotExist()
    }

    @Test
    fun recreationPreservesSelectedYearModuleAndMatchingCourseInputs() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { TestAuthenticatedApp() } } }
        previousYear()
        onNodeWithContentDescription("学期を選択").performClick()
        onNodeWithText("秋A").performClick()
        navigate(AppTab.Courses)
        onNodeWithText("Name or keyword").performTextInput("2025 query")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }

        onNodeWithTag("screen-Courses").assertIsDisplayed()
        onNodeWithTag("selected-year").assertTextContains("2025年度")
        onNodeWithText("Name or keyword").assertTextContains("2025 query")
        navigate(AppTab.Timetable)
        onNodeWithText("秋A").assertIsDisplayed()
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("drawer-year").assertTextContains("2025年度")
    }

    @Test
    fun failedSessionRestorationOffersLoginInsteadOfAnEndlessSpinner() = runComposeUiTest {
        setContent {
            TwinteTheme {
                AuthenticationGate(
                    uiState = AuthUiState(AuthState.Unknown, "Network error", isLoading = false),
                    isGoogleConfigured = true,
                    isAppleConfigured = false,
                    onGoogleSignIn = {},
                    onAppleSignIn = {},
                ) { Text("Protected") }
            }
        }
        onNodeWithText("Network error").assertIsDisplayed()
        onNodeWithText("Google").assertIsDisplayed()
        onNodeWithText("Protected").assertDoesNotExist()
    }

    @Test
    fun rendersNavigationAtPhoneAndTabletSizes() {
        listOf(Size(360f, 800f), Size(800f, 1100f)).forEach { size ->
            runSkikoComposeUiTest(size = size, density = Density(1f)) {
                setContent { TwinteTheme { TestAuthenticatedApp(withCourse = true) } }
                AppTab.entries.forEach { tab ->
                    navigate(tab)
                    onNodeWithTag("screen-${tab.root}").assertIsDisplayed()
                    onNodeWithContentDescription("メニュー").assertIsDisplayed()
                    val file = File("build/reports/navigation/${size.width.toInt()}x${size.height.toInt()}-${tab.name}.png")
                    file.parentFile.mkdirs()
                    Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { data -> file.writeBytes(data.bytes) }
                    }
                }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.navigate(tab: AppTab) {
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithTag("destination-${tab.name}").performClick()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.previousYear() {
        onNodeWithContentDescription("メニュー").performClick()
        onNodeWithContentDescription("Previous year").performClick()
        onNodeWithTag("destination-Timetable").performClick()
    }
}

// Skiko's StateRestorationTester has no platform codec. Exercise the real registry instead.
internal class SaveableComposition {
    private var visible by mutableStateOf(true)
    private var registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
    private var saved = emptyMap<String, List<Any?>>()

    @Composable
    fun Content(content: @Composable () -> Unit) {
        if (visible) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
        }
    }

    fun saveAndDispose() {
        saved = registry.performSave()
        visible = false
    }

    fun restore() {
        registry = SaveableStateRegistry(restoredValues = saved, canBeSaved = { true })
        visible = true
    }
}

package net.twinte.mobile_experiments

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.CompletableDeferred
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.timetable.DailyCourseSlot
import net.twinte.mobile_experiments.core.timetable.DailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import kotlin.time.Instant
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppNavigationUiTest {
    @Test
    fun tabsAndNestedBackNavigationRetainSearchInput() = runComposeUiTest {
        setContent { TwinteTheme { NavigationContent() } }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Seminar")
        onNodeWithTag("tab-Account").performClick()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")

        onNodeWithText("Manage tags").performScrollTo().performClick()
        onNodeWithText("New tag").performTextInput("Important")
        onNodeWithTag("tab-Today").performClick()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("New tag").assertTextContains("Important")
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")
    }

    @Test
    fun recreationRestoresTabNestedRouteAndInputs() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { NavigationContent() } } }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Seminar")
        onNodeWithText("Manage tags").performScrollTo().performClick()
        onNodeWithText("New tag").performTextInput("Important")

        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }

        onNodeWithTag("tab-Courses").assertIsSelected()
        onNodeWithText("New tag").assertTextContains("Important")
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")
    }

    @Test
    fun recreationPreservesUnsavedCourseDraft() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { NavigationContent() } } }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Create course").performScrollTo().performClick()
        onNodeWithText("Name", substring = false).performTextInput("New seminar")
        onNodeWithText("Credit (e.g. 1.0)").performTextInput("1.5")

        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }

        onNodeWithText("Name", substring = false).assertTextContains("New seminar")
        onNodeWithText("Credit (e.g. 1.0)").assertTextContains("1.5")
        onNodeWithText("Cancel").performClick()
        onNodeWithTag("tab-Courses").assertIsSelected()
    }

    @Test
    fun logoutRemovesProtectedScreensAndLoginStartsAtHome() = runComposeUiTest {
        var auth by mutableStateOf(signedIn())
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) {
                    NavigationContent(auth)
                }
            }
        }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Private query")
        runOnIdle { auth = AuthUiState(AuthState.LoggedOut, "Signed out") }
        onNodeWithText("Google").assertIsDisplayed()
        onNodeWithTag("tab-Courses").assertDoesNotExist()

        runOnIdle { auth = signedIn() }
        onNodeWithTag("tab-Timetable").assertIsSelected()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Private query").assertDoesNotExist()
    }

    @Test
    fun switchingAccountsResetsNavigationAndPrivateInputs() = runComposeUiTest {
        var auth by mutableStateOf(signedIn())
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) {
                    NavigationContent(auth)
                }
            }
        }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Private query")
        runOnIdle { auth = signedIn("other-user") }

        onNodeWithTag("tab-Timetable").assertIsSelected()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Private query").assertDoesNotExist()
    }

    @Test
    fun savedNavigationIsNotRestoredForADifferentAccount() = runComposeUiTest {
        var auth by mutableStateOf(signedIn())
        val restoration = SaveableComposition()
        setContent {
            restoration.Content {
                TwinteTheme {
                    AuthenticationGate(auth, true, false, {}, {}) { NavigationContent(auth) }
                }
            }
        }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Private query")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle {
            auth = signedIn("other-user")
            restoration.restore()
        }
        onNodeWithTag("tab-Timetable").assertIsSelected()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Private query").assertDoesNotExist()
    }

    @Test
    fun refreshingTheSameAccountPreservesCurrentRoute() = runComposeUiTest {
        var auth by mutableStateOf(signedIn())
        setContent {
            TwinteTheme {
                AuthenticationGate(auth, true, false, {}, {}) { NavigationContent(auth) }
            }
        }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Seminar")
        runOnIdle { auth = signedIn().copy(message = "Checked session") }
        onNodeWithTag("tab-Courses").assertIsSelected()
        onNodeWithText("Name or keyword").assertTextContains("Seminar")
    }

    @Test
    fun changingYearWhileCoursesAreHiddenResetsTheirSavedInputs() = runComposeUiTest {
        setContent { TwinteTheme { NavigationContent() } }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("Previous year query")
        onNodeWithTag("tab-Timetable").performClick()
        onNodeWithContentDescription("Previous year").performClick()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("2025年度").assertIsDisplayed()
        onNodeWithText("Previous year query").assertDoesNotExist()
    }

    @Test
    fun switchingTabsDoesNotCancelAPendingCourseSearch() = runComposeUiTest {
        val pending = CompletableDeferred<Unit>()
        val service = FakeTimetableService(searchResultProvider = {
            pending.await()
            emptyList()
        })
        setContent { TwinteTheme { NavigationContent(service = service) } }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Search", substring = false).performClick()
        onNodeWithText("Searching courses...").assertIsDisplayed()
        onNodeWithTag("tab-Today").performClick()
        runOnIdle { pending.complete(Unit) }
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("No courses found").assertIsDisplayed()
    }

    @Test
    fun leavingTheAuthenticatedGraphCancelsAPendingCourseMutation() = runComposeUiTest {
        var auth by mutableStateOf(signedIn())
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
                    NavigationContent(auth, service = service)
                }
            }
        }
        onNodeWithTag("tab-Courses").performClick()
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
        onNodeWithTag("tab-Courses").assertDoesNotExist()
    }

    @Test
    fun recreationPreservesSelectedYearModuleAndMatchingCourseInputs() = runComposeUiTest {
        val restoration = SaveableComposition()
        setContent { restoration.Content { TwinteTheme { NavigationContent() } } }
        onNodeWithContentDescription("Previous year").performClick()
        onNodeWithText("秋A").performClick()
        onNodeWithTag("tab-Courses").performClick()
        onNodeWithText("Name or keyword").performTextInput("2025 query")
        runOnIdle { restoration.saveAndDispose() }
        runOnIdle { restoration.restore() }

        onNodeWithTag("tab-Courses").assertIsSelected()
        onNodeWithText("2025年度").assertIsDisplayed()
        onNodeWithText("Name or keyword").assertTextContains("2025 query")
        onNodeWithTag("tab-Timetable").performClick()
        onNodeWithText("2025年度").assertIsDisplayed()
        onNodeWithText("秋A").assertIsSelected()
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
                setContent { TwinteTheme { NavigationContent(withCourse = true) } }
                AppTab.entries.forEach { tab ->
                    onNodeWithTag("tab-${tab.name}").performClick().assertIsSelected()
                    AppTab.entries.forEach { visibleTab ->
                        onNodeWithTag("tab-${visibleTab.name}").assertIsDisplayed()
                    }
                    val file = File("build/reports/navigation/${size.width.toInt()}x${size.height.toInt()}-${tab.name}.png")
                    file.parentFile.mkdirs()
                    Image.makeFromBitmap(captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { data -> file.writeBytes(data.bytes) }
                    }
                }
            }
        }
    }

    @Composable
    private fun NavigationContent(
        auth: AuthUiState = signedIn(),
        withCourse: Boolean = false,
        service: FakeTimetableService? = null,
    ) {
        val scope = rememberCoroutineScope()
        val authStateHolder = remember(scope) {
            AuthStateHolder(
                authService = FakeAuthService(),
                googleIdTokenProvider = UnavailableGoogleIdTokenProvider,
                appleSignInCredentialProvider = UnavailableAppleSignInCredentialProvider,
                scope = scope,
            )
        }
        val timetableService = remember(withCourse, service) {
            service ?: FakeTimetableService(
                initialSnapshot = TimetableSnapshot(
                    courses = if (withCourse) listOf(course) else emptyList(),
                    lastSuccessfulSyncAt = Instant.parse("2026-11-06T00:00:00Z"),
                ),
            )
        }
        val dailyService = remember {
            val date = LocalDate(2026, 11, 6)
            FakeDailyTimetableService(
                initialSnapshot = DailyTimetableSnapshot(
                    timetable = DailyTimetable(
                        date, Module.FallB, Day.Friday, "Normal",
                        listOf(DailyCourseSlot.Course(2, "10:10", "11:25", course.id, course.name, "3A204")),
                    ),
                    lastSuccessfulSyncAt = Instant.parse("2026-11-06T00:00:00Z"),
                ),
            )
        }
        val timetableStateHolder = rememberTimetableStateHolder(
            timetableService = timetableService,
            dailyTimetableService = dailyService,
            initialDate = LocalDate(2026, 11, 6),
            scope = scope,
            onFailure = authStateHolder::handleExternalFailure,
        )
        val accountId = (auth.authState as? AuthState.LoggedIn)?.user?.id
        LaunchedEffect(accountId, timetableStateHolder) {
            timetableStateHolder.bind(accountId, LocalDate(2026, 11, 6))
        }
        AppNavigation(
            authUiState = auth,
            authStateHolder = authStateHolder,
            timetableStateHolder = timetableStateHolder,
            isGoogleConfigured = true,
            isAppleConfigured = true,
        )
    }

    private fun signedIn(id: String = "user") = AuthUiState(
        AuthState.LoggedIn(
            AuthSession(
                sessionId = "session",
                user = User(id, listOf(UserAuthentication(AuthProvider.Google, "social-id"))),
            ),
        ),
        message = "Signed in",
    )

    private val course = RegisteredCourse(
        id = "course",
        year = 2026,
        name = "Software Engineering Seminar",
        instructors = "Teacher",
        credit = "1.5",
        methods = listOf(CourseMethod.FaceToFace),
        schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 2, "3A204")),
    )
}

// Skiko's StateRestorationTester has no platform codec. Exercise the real registry instead.
private class SaveableComposition {
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

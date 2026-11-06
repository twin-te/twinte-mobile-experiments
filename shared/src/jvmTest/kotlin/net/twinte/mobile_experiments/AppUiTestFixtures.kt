package net.twinte.mobile_experiments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.auth.AuthSession
import net.twinte.mobile_experiments.core.auth.AuthState
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import net.twinte.mobile_experiments.core.timetable.DailyCourseSlot
import net.twinte.mobile_experiments.core.timetable.DailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableSnapshot
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import kotlin.time.Instant

internal fun testSignedIn(id: String = "user", offline: Boolean = false) = AuthUiState(
    AuthState.LoggedIn(
        AuthSession("session", User(id, listOf(UserAuthentication(AuthProvider.Google, "social-id"))), offline),
    ),
    message = if (offline) "Offline; using saved data" else "Signed in",
)

@Composable
internal fun TestAuthenticatedApp(
    auth: AuthUiState = testSignedIn(),
    withCourse: Boolean = false,
    service: FakeTimetableService? = null,
    dailyService: DailyTimetableService? = null,
    date: LocalDate = LocalDate(2026, 11, 6),
) {
    val scope = rememberCoroutineScope()
    val authStateHolder = remember(scope) {
        AuthStateHolder(
            FakeAuthService(), UnavailableGoogleIdTokenProvider, UnavailableAppleSignInCredentialProvider, scope,
        )
    }
    val timetableService = remember(withCourse, service) {
        service ?: FakeTimetableService(
            initialSnapshot = TimetableSnapshot(
                courses = if (withCourse) testTimetableCourses else emptyList(),
                lastSuccessfulSyncAt = Instant.parse("2026-11-06T00:00:00Z"),
            ),
        )
    }
    val daily = remember(dailyService, date) {
        dailyService ?: FakeDailyTimetableService(
            initialSnapshot = DailyTimetableSnapshot(
                DailyTimetable(
                    date, Module.FallB, Day.Friday, "通常日課",
                    listOf(DailyCourseSlot.Course(2, "10:10", "11:25", "course", "情報メディア実験A", "3A204")),
                ),
                Instant.parse("2026-11-06T00:00:00Z"),
            ),
        )
    }
    val timetableStateHolder = rememberTimetableStateHolder(
        timetableService, daily, date, scope, authStateHolder::handleExternalFailure,
    )
    val accountId = (auth.authState as? AuthState.LoggedIn)?.user?.id
    LaunchedEffect(accountId, timetableStateHolder, date) {
        timetableStateHolder.bind(accountId, date)
    }
    AppNavigation(auth, authStateHolder, timetableStateHolder, true, true)
}

internal val testTimetableCourses = listOf(
    regularCourse("course", "情報メディア実験A", Day.Monday, 2, "3A204"),
    regularCourse("duplicate", "線形代数I", Day.Monday, 2, "1E101"),
    regularCourse("education", "コンピュータと教育", Day.Tuesday, 1, "3A308"),
    regularCourse("software", "ソフトウェア工学", Day.Wednesday, 3, "4B201"),
    regularCourse("long", "社会・国際学群専門科目の長い授業名サンプル", Day.Thursday, 4, "オンライン"),
    regularCourse("algorithm", "データ構造とアルゴリズム", Day.Friday, 1, "3A210"),
    regularCourse("intensive", "集中講義・情報学特別演習", Day.Intensive, 0, "3A204").copy(
        schedules = listOf(CourseSchedule(Module.SummerVacation, Day.Intensive, 0, "3A204")),
    ),
    regularCourse("saturday", "土曜演習", Day.Saturday, 1, "1D201"),
)

private fun regularCourse(id: String, name: String, day: Day, period: Int, room: String) = RegisteredCourse(
    id = id, year = 2026, name = name, instructors = "筑波 太郎", credit = "1.5",
    methods = listOf(CourseMethod.FaceToFace),
    schedules = listOf(
        CourseSchedule(Module.SpringA, day, period, room),
        CourseSchedule(Module.FallB, day, period, room),
    ),
)

package net.twinte.mobile_experiments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.twinte.mobile_experiments.core.api.ktor.KtorAppleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.KtorGoogleSessionApi
import net.twinte.mobile_experiments.core.api.ktor.KtorTimetableApi
import net.twinte.mobile_experiments.core.api.ktor.KtorUnifiedApi
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteHttpClient
import net.twinte.mobile_experiments.core.api.ktor.rememberTwinteLoginHttpClient
import net.twinte.mobile_experiments.core.auth.AuthRepository
import net.twinte.mobile_experiments.core.auth.AuthService
import net.twinte.mobile_experiments.core.auth.AuthenticatedSessionProvider
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.database.RoomAccountLocalDataSource
import net.twinte.mobile_experiments.core.database.RoomDailyTimetableLocalDataSource
import net.twinte.mobile_experiments.core.database.RoomTimetableLocalDataSource
import net.twinte.mobile_experiments.core.database.rememberTwinteDatabase
import net.twinte.mobile_experiments.core.timetable.DailyTimetableRepository
import net.twinte.mobile_experiments.core.timetable.DailyTimetableService
import net.twinte.mobile_experiments.core.timetable.TimetableRepository
import net.twinte.mobile_experiments.core.timetable.TimetableService

internal data class AppDependencies(
    val authService: AuthService,
    val timetableService: TimetableService,
    val dailyTimetableService: DailyTimetableService,
)

@Composable
internal fun rememberAppDependencies(
    sessionStore: SessionStore,
    appBaseUrl: String,
): AppDependencies {
    val normalizedAppBaseUrl = appBaseUrl.trimEnd('/').ifBlank { DefaultAppBaseUrl }
    val httpClient = rememberTwinteHttpClient()
    val loginHttpClient = rememberTwinteLoginHttpClient()
    val database = rememberTwinteDatabase()

    return remember(
        sessionStore,
        normalizedAppBaseUrl,
        httpClient,
        loginHttpClient,
        database,
    ) {
        val accountLocalDataSource = RoomAccountLocalDataSource(database.accountDao())
        val authenticatedSessionProvider = AuthenticatedSessionProvider(
            sessionStore = sessionStore,
            accountLocalDataSource = accountLocalDataSource,
        )
        AppDependencies(
            authService = AuthRepository(
                sessionStore = sessionStore,
                authApi = KtorAuthApi(apiBaseUrl = "$normalizedAppBaseUrl/api/v4", httpClient = httpClient),
                googleSessionApi = KtorGoogleSessionApi(
                    appBaseUrl = normalizedAppBaseUrl,
                    httpClient = loginHttpClient,
                ),
                appleSessionApi = KtorAppleSessionApi(
                    appBaseUrl = normalizedAppBaseUrl,
                    httpClient = loginHttpClient,
                ),
                accountLocalDataSource = accountLocalDataSource,
            ),
            timetableService = TimetableRepository(
                authenticatedSessionProvider = authenticatedSessionProvider,
                timetableApi = KtorTimetableApi(
                    apiBaseUrl = "$normalizedAppBaseUrl/api/v4",
                    httpClient = httpClient,
                ),
                localDataSource = RoomTimetableLocalDataSource(database.timetableDao()),
            ),
            dailyTimetableService = DailyTimetableRepository(
                authenticatedSessionProvider = authenticatedSessionProvider,
                api = KtorUnifiedApi(apiBaseUrl = "$normalizedAppBaseUrl/api/v4", httpClient = httpClient),
                localDataSource = RoomDailyTimetableLocalDataSource(database.dailyTimetableDao()),
            ),
        )
    }
}

internal const val DefaultAppBaseUrl = "https://app.twinte.net"

package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot
import net.twinte.mobile_experiments.core.api.DailyTimetableApi
import net.twinte.mobile_experiments.core.auth.AuthenticatedSessionProvider
import net.twinte.mobile_experiments.core.auth.MemoryAccountLocalDataSource
import net.twinte.mobile_experiments.core.auth.MemorySessionStore
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class DailyTimetableRepositoryTest {
    private val date = LocalDate(2026, 5, 1)

    @Test
    fun refreshPersistsApiSnapshotAndBuildsDailyTimetable() = runTest {
        val apiSnapshot = DailyApiSnapshot(
            date = date,
            module = Module.SpringA,
            events = listOf(
                SchoolCalendarEvent(
                    id = 1,
                    type = EventType.SubstituteDay,
                    date = date,
                    description = "",
                    changeTo = Day.Monday,
                ),
            ),
            courses = emptyList(),
        )
        val localDataSource = FakeDailyTimetableLocalDataSource()
        val repository = repository(
            api = FakeDailyTimetableApi(Result.success(apiSnapshot)),
            localDataSource = localDataSource,
        )

        repository.refresh("user-id", date)

        val cached = repository.observe("user-id", date).first()
        assertEquals(date, cached?.timetable?.date)
        assertEquals(Day.Monday, cached?.timetable?.effectiveDay)
        assertEquals(Instant.fromEpochMilliseconds(1234), cached?.lastSuccessfulSyncAt)
    }

    @Test
    fun failedRefreshKeepsExistingSnapshot() = runTest {
        val cached = CachedDailyTimetable(
            snapshot = DailyApiSnapshot(
                date = date,
                module = Module.SpringA,
                events = emptyList(),
                courses = emptyList(),
            ),
            lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(100),
        )
        val localDataSource = FakeDailyTimetableLocalDataSource(cached)
        val repository = repository(
            api = FakeDailyTimetableApi(Result.failure(IllegalStateException("offline"))),
            localDataSource = localDataSource,
        )

        assertFailsWith<IllegalStateException> {
            repository.refresh("user-id", date)
        }

        assertEquals(
            Instant.fromEpochMilliseconds(100),
            repository.observe("user-id", date).first()?.lastSuccessfulSyncAt,
        )
    }

    @Test
    fun unknownDateHasNoCachedSnapshot() = runTest {
        val repository = repository(
            api = FakeDailyTimetableApi(Result.failure(IllegalStateException("unused"))),
            localDataSource = FakeDailyTimetableLocalDataSource(),
        )

        assertNull(repository.observe("user-id", date).first())
    }

    private fun repository(
        api: DailyTimetableApi,
        localDataSource: DailyTimetableLocalDataSource,
    ): DailyTimetableRepository =
        DailyTimetableRepository(
            authenticatedSessionProvider = AuthenticatedSessionProvider(
                sessionStore = MemorySessionStore("session-id"),
                accountLocalDataSource = MemoryAccountLocalDataSource(User("user-id", emptyList())),
            ),
            api = api,
            localDataSource = localDataSource,
            now = { Instant.fromEpochMilliseconds(1234) },
        )
}

private class FakeDailyTimetableApi(
    private val result: Result<DailyApiSnapshot>,
) : DailyTimetableApi {
    override suspend fun getByDate(session: TwinteSession, date: LocalDate): DailyApiSnapshot =
        result.getOrThrow()
}

private class FakeDailyTimetableLocalDataSource(
    initial: CachedDailyTimetable? = null,
) : DailyTimetableLocalDataSource {
    private val snapshot = MutableStateFlow(initial)

    override fun observe(accountId: String, date: LocalDate): Flow<CachedDailyTimetable?> =
        snapshot

    override suspend fun replace(
        accountId: String,
        snapshot: DailyApiSnapshot,
        syncedAt: Instant,
    ) {
        this.snapshot.value = CachedDailyTimetable(snapshot, syncedAt)
    }
}

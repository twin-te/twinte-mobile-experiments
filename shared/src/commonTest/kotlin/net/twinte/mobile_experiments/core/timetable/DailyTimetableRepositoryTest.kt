package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun refreshAfterMutationWaitsForOlderRefreshBeforeUpdatingCache() = runTest {
        val pendingRefresh = CompletableDeferred<Unit>()
        var requestCount = 0
        val api = object : DailyTimetableApi {
            override suspend fun getByDate(session: TwinteSession, date: LocalDate): DailyApiSnapshot {
                requestCount += 1
                val module = if (requestCount == 1) {
                    pendingRefresh.await()
                    Module.SpringA
                } else {
                    Module.FallA
                }
                return DailyApiSnapshot(date, module, emptyList(), emptyList())
            }
        }
        val repository = repository(api, FakeDailyTimetableLocalDataSource())
        val firstRefresh = launch { repository.refresh("user-id", date) }
        runCurrent()
        val secondRefresh = launch { repository.refresh("user-id", date) }
        runCurrent()

        assertEquals(1, requestCount)
        pendingRefresh.complete(Unit)
        firstRefresh.join()
        secondRefresh.join()

        assertEquals(Module.FallA, repository.observe("user-id", date).first()?.timetable?.module)
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

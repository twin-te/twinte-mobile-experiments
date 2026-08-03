package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot
import net.twinte.mobile_experiments.core.api.DailyTimetableApi
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
                    date = date.toString(),
                    description = "",
                    changeTo = Day.Monday,
                ),
            ),
            registeredCourses = emptyList(),
        )
        val localDataSource = FakeDailyTimetableLocalDataSource()
        val repository = repository(
            api = FakeDailyTimetableApi(Result.success(apiSnapshot)),
            localDataSource = localDataSource,
        )

        repository.refresh("user-id", date)

        val cached = repository.observe("user-id", date).first()
        assertEquals(date.toString(), cached?.timetable?.date)
        assertEquals(Day.Monday, cached?.timetable?.effectiveDay)
        assertEquals(1234L, cached?.lastSuccessfulSyncAtMillis)
    }

    @Test
    fun failedRefreshKeepsExistingSnapshot() = runTest {
        val cached = CachedDailyTimetable(
            snapshot = DailyApiSnapshot(
                date = date,
                module = Module.SpringA,
                events = emptyList(),
                registeredCourses = emptyList(),
            ),
            lastSuccessfulSyncAtMillis = 100L,
        )
        val localDataSource = FakeDailyTimetableLocalDataSource(cached)
        val repository = repository(
            api = FakeDailyTimetableApi(Result.failure(IllegalStateException("offline"))),
            localDataSource = localDataSource,
        )

        assertFailsWith<IllegalStateException> {
            repository.refresh("user-id", date)
        }

        assertEquals(100L, repository.observe("user-id", date).first()?.lastSuccessfulSyncAtMillis)
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
            sessionStore = MemorySessionStore("session-id"),
            accountLocalDataSource = MemoryAccountLocalDataSource(User("user-id", emptyList())),
            api = api,
            localDataSource = localDataSource,
            nowMillis = { 1234L },
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
        syncedAtMillis: Long,
    ) {
        this.snapshot.value = CachedDailyTimetable(snapshot, syncedAtMillis)
    }
}

package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import kotlin.time.Instant

data class TimetableSnapshot(
    val courses: List<RegisteredCourse> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val lastSuccessfulSyncAt: Instant? = null,
)

interface TimetableService {
    fun observeTimetable(accountId: String, year: Int? = null): Flow<TimetableSnapshot>

    suspend fun refresh(accountId: String, year: Int? = null)
}

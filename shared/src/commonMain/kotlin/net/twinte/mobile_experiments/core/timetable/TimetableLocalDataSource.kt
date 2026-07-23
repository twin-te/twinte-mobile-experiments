package net.twinte.mobile_experiments.core.timetable

import kotlinx.coroutines.flow.Flow
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag

interface TimetableLocalDataSource {
    fun observeSnapshot(accountId: String, year: Int? = null): Flow<TimetableSnapshot>

    suspend fun replaceSnapshot(
        accountId: String,
        year: Int?,
        courses: List<RegisteredCourse>,
        tags: List<Tag>,
        syncedAtMillis: Long,
    )
}

package net.twinte.mobile_experiments.core.timetable

import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.SessionStore
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.auth.getSession
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag

class TimetableRepository(
    private val sessionStore: SessionStore,
    private val timetableApi: TimetableApi,
) : TimetableService {
    override suspend fun listRegisteredCourses(year: Int?): List<RegisteredCourse> =
        withAuthenticatedSession { session ->
            timetableApi.listRegisteredCourses(session, year)
        }

    override suspend fun listTags(): List<Tag> =
        withAuthenticatedSession { session ->
            timetableApi.listTags(session)
        }

    private suspend fun requireSession(): TwinteSession =
        requireNotNull(sessionStore.getSession()) { "No session" }

    private suspend fun <T> withAuthenticatedSession(block: suspend (TwinteSession) -> T): T {
        val session = requireSession()
        return runCatching {
            block(session)
        }.onFailure { error ->
            if (error is TwinteApiException && error.isUnauthorized) {
                sessionStore.clearSessionId()
            }
        }.getOrThrow()
    }
}

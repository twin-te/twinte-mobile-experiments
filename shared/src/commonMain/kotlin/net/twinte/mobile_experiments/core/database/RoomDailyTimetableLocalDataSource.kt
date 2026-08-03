package net.twinte.mobile_experiments.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.timetable.CachedDailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableLocalDataSource

class RoomDailyTimetableLocalDataSource(
    private val dao: DailyTimetableDao,
) : DailyTimetableLocalDataSource {
    override fun observe(accountId: String, date: LocalDate): Flow<CachedDailyTimetable?> {
        val courseDetails = combine(
            dao.observeCourses(accountId, date.toString()),
            dao.observeMethods(accountId),
            dao.observeSchedules(accountId),
            dao.observeCourseTags(accountId),
        ) { courses, methods, schedules, courseTags ->
            val methodsByCourse = methods.groupBy { it.courseId }
            val schedulesByCourse = schedules.groupBy { it.courseId }
            val tagIdsByCourse = courseTags.groupBy { it.courseId }
            courses.map { course ->
                course.toDomain(
                    methods = methodsByCourse[course.id].orEmpty(),
                    schedules = schedulesByCourse[course.id].orEmpty(),
                    tagIds = tagIdsByCourse[course.id].orEmpty().map { it.tagId },
                )
            }
        }
        return combine(
            dao.observeTimetable(accountId, date.toString()),
            dao.observeEvents(accountId, date.toString()),
            courseDetails,
        ) { timetable, events, courses ->
            timetable?.toCachedSnapshot(events, courses)
        }
    }

    override suspend fun replace(
        accountId: String,
        snapshot: DailyApiSnapshot,
        syncedAtMillis: Long,
    ) {
        val date = snapshot.date.toString()
        dao.replace(
            timetable = DailyTimetableEntity(
                accountId = accountId,
                date = date,
                module = snapshot.module.name,
                lastSuccessfulSyncAtMillis = syncedAtMillis,
            ),
            events = snapshot.events.map {
                DailyEventEntity(
                    accountId = accountId,
                    date = date,
                    eventId = it.id,
                    type = it.type.name,
                    eventDate = it.date,
                    description = it.description,
                    changeTo = it.changeTo?.name,
                )
            },
            courses = snapshot.registeredCourses.map { it.toEntity(accountId) },
            methods = snapshot.registeredCourses.flatMap { course ->
                course.methods.mapIndexed { index, method ->
                    CourseMethodEntity(
                        accountId = accountId,
                        courseId = course.id,
                        position = index,
                        method = method.name,
                    )
                }
            },
            schedules = snapshot.registeredCourses.flatMap { course ->
                course.schedules.mapIndexed { index, schedule ->
                    schedule.toEntity(accountId, course.id, index)
                }
            },
            dailyCourses = snapshot.registeredCourses.mapIndexed { index, course ->
                DailyCourseEntity(
                    accountId = accountId,
                    date = date,
                    courseId = course.id,
                    position = index,
                )
            },
        )
    }
}

private fun DailyTimetableEntity.toCachedSnapshot(
    events: List<DailyEventEntity>,
    courses: List<net.twinte.mobile_experiments.core.domain.RegisteredCourse>,
): CachedDailyTimetable? {
    val module = Module.entries.firstOrNull { it.name == module } ?: return null
    return CachedDailyTimetable(
        snapshot = DailyApiSnapshot(
            date = LocalDate.parse(date),
            module = module,
            events = events.mapNotNull(DailyEventEntity::toDomain),
            registeredCourses = courses,
        ),
        lastSuccessfulSyncAtMillis = lastSuccessfulSyncAtMillis,
    )
}

private fun DailyEventEntity.toDomain(): SchoolCalendarEvent? {
    val type = EventType.entries.firstOrNull { it.name == type } ?: return null
    val changeTo = changeTo?.let { name ->
        Day.entries.firstOrNull { it.name == name } ?: return null
    }
    return SchoolCalendarEvent(
        id = eventId,
        type = type,
        date = eventDate,
        description = description,
        changeTo = changeTo,
    )
}

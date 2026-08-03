package net.twinte.mobile_experiments.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.DailyCourse
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.timetable.CachedDailyTimetable
import net.twinte.mobile_experiments.core.timetable.DailyTimetableLocalDataSource
import kotlin.time.Instant

class RoomDailyTimetableLocalDataSource(
    private val dao: DailyTimetableDao,
) : DailyTimetableLocalDataSource {
    override fun observe(accountId: String, date: LocalDate): Flow<CachedDailyTimetable?> {
        val courseDetails = combine(
            dao.observeCourses(accountId, date.toString()),
            dao.observeMethods(accountId, date.toString()),
            dao.observeSchedules(accountId, date.toString()),
        ) { courses, methods, schedules ->
            val methodsByCourse = methods.groupBy { it.courseId }
            val schedulesByCourse = schedules.groupBy { it.courseId }
            courses.map { course ->
                course.toDomain(
                    methods = methodsByCourse[course.id].orEmpty(),
                    schedules = schedulesByCourse[course.id].orEmpty(),
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
        syncedAt: Instant,
    ) {
        val date = snapshot.date.toString()
        dao.replace(
            timetable = DailyTimetableEntity(
                accountId = accountId,
                date = date,
                module = snapshot.module.name,
                lastSuccessfulSyncAtMillis = syncedAt.toEpochMilliseconds(),
            ),
            events = snapshot.events.map {
                DailyEventEntity(
                    accountId = accountId,
                    date = date,
                    eventId = it.id,
                    type = it.type.name,
                    eventDate = it.date.toString(),
                    description = it.description,
                    changeTo = it.changeTo?.name,
                )
            },
            courses = snapshot.courses.mapIndexed { index, course ->
                course.toEntity(accountId, date, index)
            },
            methods = snapshot.courses.flatMap { course ->
                course.methods.mapIndexed { index, method ->
                    DailyCourseMethodEntity(
                        accountId = accountId,
                        date = date,
                        courseId = course.id,
                        position = index,
                        method = method.name,
                    )
                }
            },
            schedules = snapshot.courses.flatMap { course ->
                course.schedules.mapIndexed { index, schedule ->
                    schedule.toDailyEntity(accountId, date, course.id, index)
                }
            },
        )
    }
}

private fun DailyTimetableEntity.toCachedSnapshot(
    events: List<DailyEventEntity>,
    courses: List<DailyCourse>,
): CachedDailyTimetable? {
    val module = Module.entries.firstOrNull { it.name == module } ?: return null
    return CachedDailyTimetable(
        snapshot = DailyApiSnapshot(
            date = LocalDate.parse(date),
            module = module,
            events = events.mapNotNull(DailyEventEntity::toDomain),
            courses = courses,
        ),
        lastSuccessfulSyncAt = Instant.fromEpochMilliseconds(lastSuccessfulSyncAtMillis),
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
        date = runCatching { LocalDate.parse(eventDate) }.getOrNull() ?: return null,
        description = description,
        changeTo = changeTo,
    )
}

private fun DailyCourse.toEntity(
    accountId: String,
    date: String,
    position: Int,
): DailyCourseEntity =
    DailyCourseEntity(
        accountId = accountId,
        date = date,
        id = id,
        position = position,
        year = year,
        name = name,
        instructors = instructors,
        credit = credit,
        memo = memo,
        attendance = attendance,
        absence = absence,
        late = late,
        code = code,
    )

private fun DailyCourseEntity.toDomain(
    methods: List<DailyCourseMethodEntity>,
    schedules: List<DailyCourseScheduleEntity>,
): DailyCourse =
    DailyCourse(
        id = id,
        year = year,
        name = name,
        instructors = instructors,
        credit = credit,
        methods = methods.mapNotNull { entity ->
            CourseMethod.entries.firstOrNull { it.name == entity.method }
        },
        schedules = schedules.mapNotNull(DailyCourseScheduleEntity::toDomain),
        memo = memo,
        attendance = attendance,
        absence = absence,
        late = late,
        code = code,
    )

private fun CourseSchedule.toDailyEntity(
    accountId: String,
    date: String,
    courseId: String,
    position: Int,
): DailyCourseScheduleEntity =
    DailyCourseScheduleEntity(
        accountId = accountId,
        date = date,
        courseId = courseId,
        position = position,
        module = module.name,
        day = day.name,
        period = period,
        locations = locations,
    )

private fun DailyCourseScheduleEntity.toDomain(): CourseSchedule? {
    val module = Module.entries.firstOrNull { it.name == module } ?: return null
    val day = Day.entries.firstOrNull { it.name == day } ?: return null
    return CourseSchedule(
        module = module,
        day = day,
        period = period,
        locations = locations,
    )
}

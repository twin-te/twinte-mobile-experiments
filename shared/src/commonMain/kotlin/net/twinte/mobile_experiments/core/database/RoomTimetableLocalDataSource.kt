package net.twinte.mobile_experiments.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.timetable.TimetableLocalDataSource
import net.twinte.mobile_experiments.core.timetable.TimetableSnapshot
import kotlin.time.Instant

class RoomTimetableLocalDataSource(
    private val timetableDao: TimetableDao,
) : TimetableLocalDataSource {
    override fun observeSnapshot(accountId: String, year: Int?): Flow<TimetableSnapshot> {
        val courseDetails = combine(
            timetableDao.observeCourses(accountId, year),
            timetableDao.observeMethods(accountId),
            timetableDao.observeSchedules(accountId),
            timetableDao.observeCourseTags(accountId),
        ) { courses, methods, schedules, courseTags ->
            CourseDetails(courses, methods, schedules, courseTags)
        }
        return combine(
            courseDetails,
            timetableDao.observeTags(accountId),
            timetableDao.observeSyncMetadata(accountId, timetableResource(year)),
        ) { details, tags, metadata ->
            details.toSnapshot(
                tags = tags,
                lastSuccessfulSyncAt = metadata?.lastSuccessfulSyncAtMillis?.let(Instant::fromEpochMilliseconds),
            )
        }
    }

    override suspend fun replaceSnapshot(
        accountId: String,
        year: Int?,
        courses: List<RegisteredCourse>,
        tags: List<Tag>,
        syncedAt: Instant,
    ) {
        timetableDao.replaceSnapshot(
            accountId = accountId,
            year = year,
            courses = courses.map { it.toEntity(accountId) },
            methods = courses.flatMap { course ->
                course.methods.mapIndexed { index, method ->
                    CourseMethodEntity(
                        accountId = accountId,
                        courseId = course.id,
                        position = index,
                        method = method.name,
                    )
                }
            },
            schedules = courses.flatMap { course ->
                course.schedules.mapIndexed { index, schedule ->
                    schedule.toEntity(accountId, course.id, index)
                }
            },
            tags = tags.map { it.toEntity(accountId) },
            courseTags = courses.flatMap { course ->
                course.tagIds.map { tagId ->
                    RegisteredCourseTagEntity(
                        accountId = accountId,
                        courseId = course.id,
                        tagId = tagId,
                    )
                }
            },
            syncedAtMillis = syncedAt.toEpochMilliseconds(),
        )
    }
}

private data class CourseDetails(
    val courses: List<RegisteredCourseEntity>,
    val methods: List<CourseMethodEntity>,
    val schedules: List<CourseScheduleEntity>,
    val courseTags: List<RegisteredCourseTagEntity>,
) {
    fun toSnapshot(
        tags: List<TagEntity>,
        lastSuccessfulSyncAt: Instant?,
    ): TimetableSnapshot {
        val methodsByCourse = methods.groupBy { it.courseId }
        val schedulesByCourse = schedules.groupBy { it.courseId }
        val tagIdsByCourse = courseTags.groupBy { it.courseId }
        return TimetableSnapshot(
            courses = courses.map { course ->
                course.toDomain(
                    methods = methodsByCourse[course.id].orEmpty(),
                    schedules = schedulesByCourse[course.id].orEmpty(),
                    tagIds = tagIdsByCourse[course.id].orEmpty().map { it.tagId },
                )
            },
            tags = tags.map { it.toDomain() },
            lastSuccessfulSyncAt = lastSuccessfulSyncAt,
        )
    }
}

internal fun RegisteredCourse.toEntity(accountId: String): RegisteredCourseEntity =
    RegisteredCourseEntity(
        accountId = accountId,
        id = id,
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

internal fun CourseSchedule.toEntity(
    accountId: String,
    courseId: String,
    position: Int,
): CourseScheduleEntity =
    CourseScheduleEntity(
        accountId = accountId,
        courseId = courseId,
        position = position,
        module = module.name,
        day = day.name,
        period = period,
        locations = locations,
    )

private fun Tag.toEntity(accountId: String): TagEntity =
    TagEntity(
        accountId = accountId,
        id = id,
        name = name,
        sortOrder = order,
    )

internal fun RegisteredCourseEntity.toDomain(
    methods: List<CourseMethodEntity>,
    schedules: List<CourseScheduleEntity>,
    tagIds: List<String>,
): RegisteredCourse =
    RegisteredCourse(
        id = id,
        year = year,
        name = name,
        instructors = instructors,
        credit = credit,
        methods = methods.mapNotNull { entity ->
            CourseMethod.entries.firstOrNull { it.name == entity.method }
        },
        schedules = schedules.mapNotNull(CourseScheduleEntity::toDomain),
        memo = memo,
        attendance = attendance,
        absence = absence,
        late = late,
        tagIds = tagIds,
        code = code,
    )

private fun CourseScheduleEntity.toDomain(): CourseSchedule? {
    val module = Module.entries.firstOrNull { it.name == module } ?: return null
    val day = Day.entries.firstOrNull { it.name == day } ?: return null
    return CourseSchedule(
        module = module,
        day = day,
        period = period,
        locations = locations,
    )
}

private fun TagEntity.toDomain(): Tag =
    Tag(id = id, name = name, order = sortOrder)

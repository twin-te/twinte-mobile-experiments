package net.twinte.mobile_experiments.core.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailyTimetableDao {
    @Query("SELECT * FROM daily_timetable WHERE accountId = :accountId AND date = :date")
    abstract fun observeTimetable(accountId: String, date: String): Flow<DailyTimetableEntity?>

    @Query(
        """
        SELECT daily_event.* FROM daily_event
        WHERE accountId = :accountId AND date = :date
        ORDER BY eventId
        """,
    )
    abstract fun observeEvents(accountId: String, date: String): Flow<List<DailyEventEntity>>

    @Query(
        """
        SELECT registered_course.* FROM registered_course
        INNER JOIN daily_course
            ON daily_course.accountId = registered_course.accountId
            AND daily_course.courseId = registered_course.id
        WHERE daily_course.accountId = :accountId AND daily_course.date = :date
        ORDER BY daily_course.position
        """,
    )
    abstract fun observeCourses(accountId: String, date: String): Flow<List<RegisteredCourseEntity>>

    @Query("SELECT * FROM course_method WHERE accountId = :accountId ORDER BY courseId, position")
    abstract fun observeMethods(accountId: String): Flow<List<CourseMethodEntity>>

    @Query("SELECT * FROM course_schedule WHERE accountId = :accountId ORDER BY courseId, position")
    abstract fun observeSchedules(accountId: String): Flow<List<CourseScheduleEntity>>

    @Query("SELECT * FROM registered_course_tag WHERE accountId = :accountId ORDER BY courseId, tagId")
    abstract fun observeCourseTags(accountId: String): Flow<List<RegisteredCourseTagEntity>>

    @Query("DELETE FROM daily_timetable WHERE accountId = :accountId AND date = :date")
    protected abstract suspend fun deleteTimetable(accountId: String, date: String)

    @Query("DELETE FROM course_method WHERE accountId = :accountId AND courseId IN (:courseIds)")
    protected abstract suspend fun deleteMethods(accountId: String, courseIds: List<String>)

    @Query("DELETE FROM course_schedule WHERE accountId = :accountId AND courseId IN (:courseIds)")
    protected abstract suspend fun deleteSchedules(accountId: String, courseIds: List<String>)

    @Upsert
    protected abstract suspend fun upsertCourses(courses: List<RegisteredCourseEntity>)

    @Upsert
    protected abstract suspend fun upsertMethods(methods: List<CourseMethodEntity>)

    @Upsert
    protected abstract suspend fun upsertSchedules(schedules: List<CourseScheduleEntity>)

    @Upsert
    protected abstract suspend fun insertTimetable(timetable: DailyTimetableEntity)

    @Upsert
    protected abstract suspend fun insertEvents(events: List<DailyEventEntity>)

    @Upsert
    protected abstract suspend fun insertDailyCourses(courses: List<DailyCourseEntity>)

    @Transaction
    open suspend fun replace(
        timetable: DailyTimetableEntity,
        events: List<DailyEventEntity>,
        courses: List<RegisteredCourseEntity>,
        methods: List<CourseMethodEntity>,
        schedules: List<CourseScheduleEntity>,
        dailyCourses: List<DailyCourseEntity>,
    ) {
        val courseIds = courses.map { it.id }
        if (courseIds.isNotEmpty()) {
            upsertCourses(courses)
            deleteMethods(timetable.accountId, courseIds)
            deleteSchedules(timetable.accountId, courseIds)
            if (methods.isNotEmpty()) upsertMethods(methods)
            if (schedules.isNotEmpty()) upsertSchedules(schedules)
        }

        deleteTimetable(timetable.accountId, timetable.date)
        insertTimetable(timetable)
        if (events.isNotEmpty()) insertEvents(events)
        if (dailyCourses.isNotEmpty()) insertDailyCourses(dailyCourses)
    }
}

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
        SELECT * FROM daily_event
        WHERE accountId = :accountId AND date = :date
        ORDER BY eventId
        """,
    )
    abstract fun observeEvents(accountId: String, date: String): Flow<List<DailyEventEntity>>

    @Query(
        """
        SELECT * FROM daily_course
        WHERE accountId = :accountId AND date = :date
        ORDER BY position
        """,
    )
    abstract fun observeCourses(accountId: String, date: String): Flow<List<DailyCourseEntity>>

    @Query(
        """
        SELECT * FROM daily_course_method
        WHERE accountId = :accountId AND date = :date
        ORDER BY courseId, position
        """,
    )
    abstract fun observeMethods(accountId: String, date: String): Flow<List<DailyCourseMethodEntity>>

    @Query(
        """
        SELECT * FROM daily_course_schedule
        WHERE accountId = :accountId AND date = :date
        ORDER BY courseId, position
        """,
    )
    abstract fun observeSchedules(accountId: String, date: String): Flow<List<DailyCourseScheduleEntity>>

    @Query("DELETE FROM daily_timetable WHERE accountId = :accountId AND date = :date")
    protected abstract suspend fun deleteTimetable(accountId: String, date: String)

    @Upsert
    protected abstract suspend fun insertTimetable(timetable: DailyTimetableEntity)

    @Upsert
    protected abstract suspend fun insertEvents(events: List<DailyEventEntity>)

    @Upsert
    protected abstract suspend fun insertCourses(courses: List<DailyCourseEntity>)

    @Upsert
    protected abstract suspend fun insertMethods(methods: List<DailyCourseMethodEntity>)

    @Upsert
    protected abstract suspend fun insertSchedules(schedules: List<DailyCourseScheduleEntity>)

    @Transaction
    open suspend fun replace(
        timetable: DailyTimetableEntity,
        events: List<DailyEventEntity>,
        courses: List<DailyCourseEntity>,
        methods: List<DailyCourseMethodEntity>,
        schedules: List<DailyCourseScheduleEntity>,
    ) {
        deleteTimetable(timetable.accountId, timetable.date)
        insertTimetable(timetable)
        if (events.isNotEmpty()) insertEvents(events)
        if (courses.isNotEmpty()) insertCourses(courses)
        if (methods.isNotEmpty()) insertMethods(methods)
        if (schedules.isNotEmpty()) insertSchedules(schedules)
    }
}

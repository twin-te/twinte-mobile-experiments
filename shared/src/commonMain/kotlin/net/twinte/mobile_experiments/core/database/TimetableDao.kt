package net.twinte.mobile_experiments.core.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TimetableDao {
    @Query(
        """
        SELECT * FROM registered_course
        WHERE accountId = :accountId AND (:year IS NULL OR year = :year)
        ORDER BY year DESC, name, id
        """,
    )
    abstract fun observeCourses(accountId: String, year: Int?): Flow<List<RegisteredCourseEntity>>

    @Query("SELECT * FROM course_method WHERE accountId = :accountId ORDER BY courseId, position")
    abstract fun observeMethods(accountId: String): Flow<List<CourseMethodEntity>>

    @Query("SELECT * FROM course_schedule WHERE accountId = :accountId ORDER BY courseId, position")
    abstract fun observeSchedules(accountId: String): Flow<List<CourseScheduleEntity>>

    @Query("SELECT * FROM registered_course_tag WHERE accountId = :accountId ORDER BY courseId, tagId")
    abstract fun observeCourseTags(accountId: String): Flow<List<RegisteredCourseTagEntity>>

    @Query("SELECT * FROM tag WHERE accountId = :accountId ORDER BY sortOrder, id")
    abstract fun observeTags(accountId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM sync_metadata WHERE accountId = :accountId AND resource = :resource")
    abstract fun observeSyncMetadata(accountId: String, resource: String): Flow<SyncMetadataEntity?>

    @Query("DELETE FROM registered_course WHERE accountId = :accountId")
    protected abstract suspend fun deleteAllCourses(accountId: String)

    @Query("DELETE FROM registered_course WHERE accountId = :accountId AND year = :year")
    protected abstract suspend fun deleteCoursesByYear(accountId: String, year: Int)

    @Query("DELETE FROM registered_course WHERE accountId = :accountId AND id NOT IN (:retainedCourseIds)")
    protected abstract suspend fun deleteCoursesNotIn(accountId: String, retainedCourseIds: List<String>)

    @Query(
        """
        DELETE FROM registered_course
        WHERE accountId = :accountId AND year = :year AND id NOT IN (:retainedCourseIds)
        """,
    )
    protected abstract suspend fun deleteCoursesByYearNotIn(
        accountId: String,
        year: Int,
        retainedCourseIds: List<String>,
    )

    @Query("DELETE FROM course_method WHERE accountId = :accountId AND courseId IN (:courseIds)")
    protected abstract suspend fun deleteMethods(accountId: String, courseIds: List<String>)

    @Query("DELETE FROM course_schedule WHERE accountId = :accountId AND courseId IN (:courseIds)")
    protected abstract suspend fun deleteSchedules(accountId: String, courseIds: List<String>)

    @Query("DELETE FROM registered_course_tag WHERE accountId = :accountId AND courseId IN (:courseIds)")
    protected abstract suspend fun deleteCourseTags(accountId: String, courseIds: List<String>)

    @Query("DELETE FROM tag WHERE accountId = :accountId")
    protected abstract suspend fun deleteTags(accountId: String)

    @Query("DELETE FROM tag WHERE accountId = :accountId AND id NOT IN (:retainedTagIds)")
    protected abstract suspend fun deleteTagsNotIn(accountId: String, retainedTagIds: List<String>)

    @Query("DELETE FROM registered_course WHERE accountId = :accountId AND id = :courseId")
    abstract suspend fun deleteCourse(accountId: String, courseId: String)

    @Query("DELETE FROM tag WHERE accountId = :accountId AND id = :tagId")
    abstract suspend fun deleteTag(accountId: String, tagId: String)

    @Upsert
    protected abstract suspend fun insertCourses(courses: List<RegisteredCourseEntity>)

    @Upsert
    protected abstract suspend fun insertMethods(methods: List<CourseMethodEntity>)

    @Upsert
    protected abstract suspend fun insertSchedules(schedules: List<CourseScheduleEntity>)

    @Upsert
    protected abstract suspend fun insertTags(tags: List<TagEntity>)

    @Upsert
    protected abstract suspend fun insertCourseTags(courseTags: List<RegisteredCourseTagEntity>)

    @Upsert
    protected abstract suspend fun saveSyncMetadata(metadata: SyncMetadataEntity)

    @Transaction
    open suspend fun upsertCourses(
        accountId: String,
        courses: List<RegisteredCourseEntity>,
        methods: List<CourseMethodEntity>,
        schedules: List<CourseScheduleEntity>,
        courseTags: List<RegisteredCourseTagEntity>,
    ) {
        val courseIds = courses.map { it.id }
        if (courseIds.isEmpty()) return

        deleteMethods(accountId, courseIds)
        deleteSchedules(accountId, courseIds)
        deleteCourseTags(accountId, courseIds)
        insertCourses(courses)
        if (methods.isNotEmpty()) insertMethods(methods)
        if (schedules.isNotEmpty()) insertSchedules(schedules)
        if (courseTags.isNotEmpty()) insertCourseTags(courseTags)
    }

    suspend fun upsertTags(tags: List<TagEntity>) {
        if (tags.isNotEmpty()) insertTags(tags)
    }

    @Transaction
    open suspend fun replaceSnapshot(
        accountId: String,
        year: Int?,
        courses: List<RegisteredCourseEntity>,
        methods: List<CourseMethodEntity>,
        schedules: List<CourseScheduleEntity>,
        tags: List<TagEntity>,
        courseTags: List<RegisteredCourseTagEntity>,
        syncedAtMillis: Long,
    ) {
        val courseIds = courses.map { it.id }
        if (year == null && courseIds.isEmpty()) {
            deleteAllCourses(accountId)
        } else if (year == null) {
            deleteCoursesNotIn(accountId, courseIds)
        } else if (courseIds.isEmpty()) {
            deleteCoursesByYear(accountId, year)
        } else {
            deleteCoursesByYearNotIn(accountId, year, courseIds)
        }

        if (courseIds.isNotEmpty()) {
            deleteMethods(accountId, courseIds)
            deleteSchedules(accountId, courseIds)
            deleteCourseTags(accountId, courseIds)
        }

        if (courses.isNotEmpty()) insertCourses(courses)
        if (methods.isNotEmpty()) insertMethods(methods)
        if (schedules.isNotEmpty()) insertSchedules(schedules)
        if (year == null) {
            deleteTags(accountId)
        }
        if (tags.isNotEmpty()) insertTags(tags)
        if (year != null) {
            if (tags.isEmpty()) {
                deleteTags(accountId)
            } else {
                deleteTagsNotIn(accountId, tags.map { it.id })
            }
        }
        if (courseTags.isNotEmpty()) insertCourseTags(courseTags)

        saveSyncMetadata(
            SyncMetadataEntity(
                accountId = accountId,
                resource = timetableResource(year),
                lastSuccessfulSyncAtMillis = syncedAtMillis,
            ),
        )
    }
}

internal fun timetableResource(year: Int?): String =
    year?.let { "timetable:$it" } ?: "timetable:all"

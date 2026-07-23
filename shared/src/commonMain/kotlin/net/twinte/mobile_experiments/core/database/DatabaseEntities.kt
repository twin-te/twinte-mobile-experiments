package net.twinte.mobile_experiments.core.database

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(tableName = "account", primaryKeys = ["id"])
data class AccountEntity(
    val id: String,
)

@Entity(
    tableName = "active_account",
    primaryKeys = ["slot"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class ActiveAccountEntity(
    val slot: Int = 0,
    val accountId: String,
)

@Entity(
    tableName = "user_authentication",
    primaryKeys = ["accountId", "provider"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class UserAuthenticationEntity(
    val accountId: String,
    val provider: String,
    val socialId: String,
)

@Entity(
    tableName = "registered_course",
    primaryKeys = ["accountId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId", "year")],
)
data class RegisteredCourseEntity(
    val accountId: String,
    val id: String,
    val year: Int,
    val name: String,
    val instructors: String,
    val credit: String,
    val memo: String,
    val attendance: Int,
    val absence: Int,
    val late: Int,
    val code: String?,
)

@Entity(
    tableName = "course_method",
    primaryKeys = ["accountId", "courseId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RegisteredCourseEntity::class,
            parentColumns = ["accountId", "id"],
            childColumns = ["accountId", "courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CourseMethodEntity(
    val accountId: String,
    val courseId: String,
    val position: Int,
    val method: String,
)

@Entity(
    tableName = "course_schedule",
    primaryKeys = ["accountId", "courseId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = RegisteredCourseEntity::class,
            parentColumns = ["accountId", "id"],
            childColumns = ["accountId", "courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CourseScheduleEntity(
    val accountId: String,
    val courseId: String,
    val position: Int,
    val module: String,
    val day: String,
    val period: Int,
    val locations: String,
)

@Entity(
    tableName = "tag",
    primaryKeys = ["accountId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId", "sortOrder")],
)
data class TagEntity(
    val accountId: String,
    val id: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "registered_course_tag",
    primaryKeys = ["accountId", "courseId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RegisteredCourseEntity::class,
            parentColumns = ["accountId", "id"],
            childColumns = ["accountId", "courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["accountId", "id"],
            childColumns = ["accountId", "tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId", "tagId")],
)
data class RegisteredCourseTagEntity(
    val accountId: String,
    val courseId: String,
    val tagId: String,
)

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["accountId", "resource"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SyncMetadataEntity(
    val accountId: String,
    val resource: String,
    val lastSuccessfulSyncAtMillis: Long,
)

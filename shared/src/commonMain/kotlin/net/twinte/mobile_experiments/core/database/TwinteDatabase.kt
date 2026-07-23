package net.twinte.mobile_experiments.core.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.compose.runtime.Composable

@Database(
    entities = [
        AccountEntity::class,
        ActiveAccountEntity::class,
        UserAuthenticationEntity::class,
        RegisteredCourseEntity::class,
        CourseMethodEntity::class,
        CourseScheduleEntity::class,
        TagEntity::class,
        RegisteredCourseTagEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(TwinteDatabaseConstructor::class)
abstract class TwinteDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun timetableDao(): TimetableDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TwinteDatabaseConstructor : RoomDatabaseConstructor<TwinteDatabase> {
    override fun initialize(): TwinteDatabase
}

fun buildTwinteDatabase(builder: RoomDatabase.Builder<TwinteDatabase>): TwinteDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .build()

internal const val TWINTE_DATABASE_NAME = "twinte.db"

@Composable
expect fun rememberTwinteDatabase(): TwinteDatabase

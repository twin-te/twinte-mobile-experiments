package net.twinte.mobile_experiments.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomLocalDataSourceTest {
    private val database = Room.inMemoryDatabaseBuilder<TwinteDatabase>(
        factory = TwinteDatabaseConstructor::initialize,
    )
        .setDriver(BundledSQLiteDriver())
        .build()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun timetableSnapshotRoundTripsAndIsRemovedWithAccount() = runTest {
        val accountDataSource = RoomAccountLocalDataSource(database.accountDao())
        val timetableDataSource = RoomTimetableLocalDataSource(database.timetableDao())
        val user = User(
            id = "user-id",
            authentications = listOf(
                UserAuthentication(AuthProvider.Google, "google-id"),
            ),
        )
        val course = RegisteredCourse(
            id = "course-id",
            year = 2026,
            name = "Distributed Systems",
            instructors = "Teacher",
            credit = "2.0",
            methods = listOf(CourseMethod.FaceToFace),
            schedules = listOf(
                CourseSchedule(
                    module = Module.SpringA,
                    day = Day.Monday,
                    period = 2,
                    locations = "3A204",
                ),
            ),
            memo = "Bring notes",
            attendance = 3,
            tagIds = listOf("tag-id"),
            code = "GB12345",
        )
        val tag = Tag("tag-id", "Required", 0)

        accountDataSource.saveActiveUser(user)
        timetableDataSource.replaceSnapshot(
            accountId = user.id,
            year = 2026,
            courses = listOf(course),
            tags = listOf(tag),
            syncedAtMillis = 1234L,
        )

        assertEquals(
            expected = listOf(course),
            actual = timetableDataSource.observeSnapshot(user.id, 2026).first().courses,
        )
        assertEquals(
            expected = listOf(tag),
            actual = timetableDataSource.observeSnapshot(user.id, 2026).first().tags,
        )

        accountDataSource.saveActiveUser(
            user.copy(
                authentications = user.authentications + UserAuthentication(
                    AuthProvider.Apple,
                    "apple-id",
                ),
            ),
        )

        assertEquals(
            listOf(course),
            timetableDataSource.observeSnapshot(user.id, 2026).first().courses,
        )

        accountDataSource.saveActiveUser(User("other-user-id", emptyList()))

        assertEquals(
            emptyList(),
            timetableDataSource.observeSnapshot(user.id, 2026).first().courses,
        )

        accountDataSource.clearActiveAccountAndData()

        assertEquals(
            emptyList(),
            timetableDataSource.observeSnapshot(user.id, 2026).first().courses,
        )
    }
}

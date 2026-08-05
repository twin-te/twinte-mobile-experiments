package net.twinte.mobile_experiments.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import net.twinte.mobile_experiments.core.domain.toDailyCourse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

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
            syncedAt = Instant.fromEpochMilliseconds(1234),
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

    @Test
    fun dailyTimetableIsIndependentFromFullRefreshAndIsRemovedWithAccount() = runTest {
        val accountDataSource = RoomAccountLocalDataSource(database.accountDao())
        val timetableDataSource = RoomTimetableLocalDataSource(database.timetableDao())
        val dailyDataSource = RoomDailyTimetableLocalDataSource(database.dailyTimetableDao())
        val user = User("user-id", emptyList())
        val date = LocalDate(2026, 5, 1)
        val course = RegisteredCourse(
            id = "course-id",
            year = 2026,
            name = "Distributed Systems",
            instructors = "Teacher",
            credit = "2.0",
            methods = listOf(CourseMethod.FaceToFace),
            schedules = listOf(
                CourseSchedule(Module.SpringA, Day.Monday, 2, "3A204"),
            ),
            memo = "daily",
        )

        accountDataSource.saveActiveUser(user)
        dailyDataSource.replace(
            accountId = user.id,
            snapshot = DailyApiSnapshot(
                date = date,
                module = Module.SpringA,
                events = listOf(
                    SchoolCalendarEvent(
                        id = 1,
                        type = EventType.SubstituteDay,
                        date = date,
                        description = "",
                        changeTo = Day.Monday,
                    ),
                ),
                courses = listOf(course.toDailyCourse()),
            ),
            syncedAt = Instant.fromEpochMilliseconds(1234),
        )

        assertEquals(
            listOf(course.toDailyCourse()),
            dailyDataSource.observe(user.id, date).first()?.snapshot?.courses,
        )
        assertEquals(
            emptyList(),
            timetableDataSource.observeSnapshot(user.id, year = null).first().courses,
        )

        timetableDataSource.replaceSnapshot(
            accountId = user.id,
            year = null,
            courses = listOf(course.copy(memo = "updated")),
            tags = emptyList(),
            syncedAt = Instant.fromEpochMilliseconds(5678),
        )

        assertEquals(
            "daily",
            dailyDataSource.observe(user.id, date).first()
                ?.snapshot
                ?.courses
                ?.single()
                ?.memo,
        )

        accountDataSource.clearActiveAccountAndData()

        assertNull(dailyDataSource.observe(user.id, date).first())
    }

    @Test
    fun timetableMutationsOnlyChangeTargetedCachedRecords() = runTest {
        val accountDataSource = RoomAccountLocalDataSource(database.accountDao())
        val timetableDataSource = RoomTimetableLocalDataSource(database.timetableDao())
        val user = User("user-id", emptyList())
        val first = RegisteredCourse(
            id = "first",
            year = 2026,
            name = "First",
            instructors = "Teacher",
            credit = "1.0",
            methods = listOf(CourseMethod.FaceToFace),
            schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 1, "Room A")),
            tagIds = listOf("first-tag"),
        )
        val second = first.copy(id = "second", name = "Second", tagIds = listOf("second-tag"))
        accountDataSource.saveActiveUser(user)
        timetableDataSource.replaceSnapshot(
            accountId = user.id,
            year = null,
            courses = listOf(first, second),
            tags = listOf(Tag("first-tag", "First tag", 0), Tag("second-tag", "Second tag", 1)),
            syncedAt = Instant.fromEpochMilliseconds(1234),
        )

        val updatedFirst = first.copy(
            name = "Updated first",
            methods = emptyList(),
            schedules = emptyList(),
            tagIds = listOf("second-tag"),
        )
        timetableDataSource.upsertCourses(user.id, listOf(updatedFirst))

        assertEquals(
            mapOf(updatedFirst.id to updatedFirst, second.id to second),
            timetableDataSource.observeSnapshot(user.id).first().courses.associateBy { it.id },
        )

        timetableDataSource.upsertTags(user.id, listOf(Tag("second-tag", "Updated tag", 0)))
        assertEquals(
            "Updated tag",
            timetableDataSource.observeSnapshot(user.id).first().tags.single { it.id == "second-tag" }.name,
        )

        timetableDataSource.deleteTag(user.id, "second-tag")
        val afterTagDelete = timetableDataSource.observeSnapshot(user.id).first()
        assertEquals(emptyList(), afterTagDelete.courses.single { it.id == "first" }.tagIds)
        assertEquals(emptyList(), afterTagDelete.courses.single { it.id == "second" }.tagIds)

        timetableDataSource.deleteCourse(user.id, "first")
        assertEquals(
            listOf(second.copy(tagIds = emptyList())),
            timetableDataSource.observeSnapshot(user.id).first().courses,
        )
    }
}

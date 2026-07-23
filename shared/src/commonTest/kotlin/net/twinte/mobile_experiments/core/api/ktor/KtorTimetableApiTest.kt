package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.twinte.api.shared.AcademicYear
import net.twinte.api.shared.UUID
import net.twinte.api.timetable.v1.ListRegisteredCoursesResponse
import net.twinte.api.timetable.v1.ListTagsResponse
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import pbandk.ExperimentalProtoJson
import pbandk.json.encodeToJsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.twinte.api.timetable.v1.CourseMethod as ApiCourseMethod
import net.twinte.api.timetable.v1.Day as ApiDay
import net.twinte.api.timetable.v1.Module as ApiModule
import net.twinte.api.timetable.v1.RegisteredCourse as ApiRegisteredCourse
import net.twinte.api.timetable.v1.Schedule as ApiSchedule
import net.twinte.api.timetable.v1.Tag as ApiTag

@OptIn(ExperimentalProtoJson::class)
class KtorTimetableApiTest {
    @Test
    fun listRegisteredCoursesPostsToConnectEndpointWithSessionCookie() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/timetable.v1.TimetableService/ListRegisteredCourses",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            assertTrue((request.body as TextContent).text.contains("2026"))
            respond(
                content = ListRegisteredCoursesResponse(
                    registeredCourses = listOf(
                        ApiRegisteredCourse(
                            id = UUID("registered-course-id"),
                            userId = UUID("user-id"),
                            year = AcademicYear(2026),
                            code = "GB12345",
                            name = "情報科学",
                            instructors = "筑波 太郎",
                            credit = "1.0",
                            methods = listOf(ApiCourseMethod.FACE_TO_FACE),
                            schedules = listOf(
                                ApiSchedule(
                                    module = ApiModule.SPRING_A,
                                    day = ApiDay.MON,
                                    period = 2,
                                    locations = "3A204",
                                ),
                            ),
                            memo = "memo",
                            attendance = 1,
                            absence = 2,
                            late = 3,
                            tagIds = listOf(UUID("tag-id")),
                        ),
                    ),
                ).encodeToJsonString(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorTimetableApi(httpClient = HttpClient(engine))

        val courses = api.listRegisteredCourses(TwinteSession("session-id"), year = 2026)

        val course = courses.single()
        assertEquals("registered-course-id", course.id)
        assertEquals(2026, course.year)
        assertEquals("GB12345", course.code)
        assertEquals("情報科学", course.name)
        assertEquals(CourseMethod.FaceToFace, course.methods.single())
        assertEquals(Module.SpringA, course.schedules.single().module)
        assertEquals(Day.Monday, course.schedules.single().day)
        assertEquals("tag-id", course.tagIds.single())
    }

    @Test
    fun listTagsPostsToConnectEndpointWithSessionCookie() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/timetable.v1.TimetableService/ListTags",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            respond(
                content = ListTagsResponse(
                    tags = listOf(
                        ApiTag(
                            id = UUID("tag-id"),
                            userId = UUID("user-id"),
                            name = "専門",
                            order = 12,
                        ),
                    ),
                ).encodeToJsonString(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorTimetableApi(httpClient = HttpClient(engine))

        val tags = api.listTags(TwinteSession("session-id"))

        assertEquals("tag-id", tags.single().id)
        assertEquals("専門", tags.single().name)
        assertEquals(12, tags.single().order)
    }
}

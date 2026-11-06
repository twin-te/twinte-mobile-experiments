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
import net.twinte.api.timetable.v1.CreateRegisteredCourseManuallyRequest
import net.twinte.api.timetable.v1.CreateRegisteredCourseManuallyResponse
import net.twinte.api.timetable.v1.CreateRegisteredCoursesByCodesResponse
import net.twinte.api.timetable.v1.CreateTagResponse
import net.twinte.api.timetable.v1.DeleteRegisteredCourseResponse
import net.twinte.api.timetable.v1.DeleteTagResponse
import net.twinte.api.timetable.v1.ListRegisteredCoursesResponse
import net.twinte.api.timetable.v1.ListTagsResponse
import net.twinte.api.timetable.v1.RearrangeTagsResponse
import net.twinte.api.timetable.v1.SearchCoursesRequest
import net.twinte.api.timetable.v1.SearchCoursesResponse
import net.twinte.api.timetable.v1.UpdateRegisteredCourseRequest
import net.twinte.api.timetable.v1.UpdateRegisteredCourseResponse
import net.twinte.api.timetable.v1.UpdateTagResponse
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import pbandk.ExperimentalProtoJson
import pbandk.json.decodeFromJsonString
import pbandk.json.encodeToJsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.twinte.api.timetable.v1.CourseMethod as ApiCourseMethod
import net.twinte.api.timetable.v1.Course as ApiCourse
import net.twinte.api.timetable.v1.Day as ApiDay
import net.twinte.api.timetable.v1.Module as ApiModule
import net.twinte.api.timetable.v1.RegisteredCourse as ApiRegisteredCourse
import net.twinte.api.timetable.v1.Schedule as ApiSchedule
import net.twinte.api.timetable.v1.Tag as ApiTag

@OptIn(ExperimentalProtoJson::class)
class KtorTimetableApiTest {
    @Test
    fun searchCoursesUsesGeneratedRequestWithoutSessionCookie() = runTest {
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/timetable.v1.TimetableService/SearchCourses",
                request.url.toString(),
            )
            assertEquals(null, request.headers[HttpHeaders.Cookie])
            val searchRequest = SearchCoursesRequest.decodeFromJsonString((request.body as TextContent).text)
            assertEquals(2026, searchRequest.year?.value)
            assertEquals(listOf("情報", "科学"), searchRequest.keywords)
            assertEquals(20, searchRequest.limit)
            respond(
                content = SearchCoursesResponse(
                    courses = listOf(
                        ApiCourse(
                            id = UUID("course-id"),
                            year = AcademicYear(2026),
                            code = "GB12345",
                            name = "情報科学",
                            instructors = "筑波 太郎",
                            credit = "1.0",
                            methods = listOf(ApiCourseMethod.FACE_TO_FACE),
                        ),
                    ),
                ).encodeToJsonString(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorTimetableApi(httpClient = HttpClient(engine))

        val courses = api.searchCourses(SearchCoursesInput(2026, keywords = listOf("情報", "科学")))

        assertEquals("course-id", courses.single().id)
        assertEquals("GB12345", courses.single().code)
        assertEquals(CourseMethod.FaceToFace, courses.single().methods.single())
    }

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

    @Test
    fun mutationMethodsUseGeneratedMessagesAndMapResponses() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            val method = request.url.segments.last()
            requests += method to (request.body as TextContent).text
            val content = when (method) {
                "CreateRegisteredCoursesByCodes" -> CreateRegisteredCoursesByCodesResponse(
                    registeredCourses = listOf(apiCourse("by-code")),
                ).encodeToJsonString()
                "CreateRegisteredCourseManually" -> CreateRegisteredCourseManuallyResponse(
                    registeredCourse = apiCourse("manual"),
                ).encodeToJsonString()
                "UpdateRegisteredCourse" -> UpdateRegisteredCourseResponse(
                    registeredCourse = apiCourse("updated"),
                ).encodeToJsonString()
                "DeleteRegisteredCourse" -> DeleteRegisteredCourseResponse().encodeToJsonString()
                "CreateTag" -> CreateTagResponse(tag = apiTag("created-tag", 0)).encodeToJsonString()
                "UpdateTag" -> UpdateTagResponse(tag = apiTag("updated-tag", 1)).encodeToJsonString()
                "DeleteTag" -> DeleteTagResponse().encodeToJsonString()
                "RearrangeTags" -> RearrangeTagsResponse(
                    tags = listOf(apiTag("second", 0), apiTag("first", 1)),
                ).encodeToJsonString()
                else -> error("Unexpected method: $method")
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorTimetableApi(httpClient = HttpClient(engine))
        val session = TwinteSession("session-id")

        assertEquals("by-code", api.createRegisteredCoursesByCodes(session, 2026, listOf("GB12345")).single().id)
        assertEquals(
            "manual",
            api.createRegisteredCourseManually(
                session,
                CreateRegisteredCourseInput(
                    year = 2026,
                    name = "Manual course",
                    instructors = "Teacher",
                    credit = "1.0",
                    methods = listOf(CourseMethod.FaceToFace),
                    schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 2, "3A204")),
                ),
            ).id,
        )
        assertEquals(
            "updated",
            api.updateRegisteredCourse(
                session,
                UpdateRegisteredCourseInput(
                    id = "course-id",
                    name = "",
                    methods = emptyList(),
                    schedules = emptyList(),
                    attendance = 0,
                    absence = 2,
                    late = 3,
                    tagIds = emptyList(),
                ),
            ).id,
        )
        api.deleteRegisteredCourse(session, "course-id")
        assertEquals("created-tag", api.createTag(session, "Required").id)
        assertEquals("updated-tag", api.updateTag(session, "tag-id", "Elective").id)
        api.deleteTag(session, "tag-id")
        assertEquals(listOf("second", "first"), api.rearrangeTags(session, listOf("second", "first")).map { it.id })

        assertEquals(
            listOf(
                "CreateRegisteredCoursesByCodes",
                "CreateRegisteredCourseManually",
                "UpdateRegisteredCourse",
                "DeleteRegisteredCourse",
                "CreateTag",
                "UpdateTag",
                "DeleteTag",
                "RearrangeTags",
            ),
            requests.map { it.first },
        )
        val manualRequest = CreateRegisteredCourseManuallyRequest.decodeFromJsonString(
            requests.first { it.first == "CreateRegisteredCourseManually" }.second,
        )
        assertEquals(ApiCourseMethod.FACE_TO_FACE, manualRequest.methods.single())
        assertEquals(ApiModule.SPRING_A, manualRequest.schedules.single().module)

        val updateRequest = UpdateRegisteredCourseRequest.decodeFromJsonString(
            requests.first { it.first == "UpdateRegisteredCourse" }.second,
        )
        assertEquals("", updateRequest.name)
        assertEquals(emptyList(), updateRequest.methods?.values)
        assertEquals(emptyList(), updateRequest.schedules?.values)
        assertEquals(0, updateRequest.attendance)
        assertEquals(2, updateRequest.absence)
        assertEquals(3, updateRequest.late)
        assertEquals(emptyList(), updateRequest.tagIds?.values)
        assertEquals(null, updateRequest.memo)
    }
}

private fun apiCourse(id: String): ApiRegisteredCourse =
    ApiRegisteredCourse(
        id = UUID(id),
        userId = UUID("user-id"),
        year = AcademicYear(2026),
        name = "Course $id",
        instructors = "Teacher",
        credit = "1.0",
    )

private fun apiTag(id: String, order: Int): ApiTag =
    ApiTag(
        id = UUID(id),
        userId = UUID("user-id"),
        name = "Tag $id",
        order = order,
    )

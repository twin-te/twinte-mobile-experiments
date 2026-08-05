package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.http.Url
import net.twinte.api.shared.AcademicYear
import net.twinte.api.shared.UUID
import net.twinte.api.shared.UUIDList
import net.twinte.api.timetable.v1.CourseMethodList
import net.twinte.api.timetable.v1.CreateRegisteredCourseManuallyRequest
import net.twinte.api.timetable.v1.CreateRegisteredCourseManuallyResponse
import net.twinte.api.timetable.v1.CreateRegisteredCoursesByCodesRequest
import net.twinte.api.timetable.v1.CreateRegisteredCoursesByCodesResponse
import net.twinte.api.timetable.v1.CreateTagRequest
import net.twinte.api.timetable.v1.CreateTagResponse
import net.twinte.api.timetable.v1.DeleteRegisteredCourseRequest
import net.twinte.api.timetable.v1.DeleteRegisteredCourseResponse
import net.twinte.api.timetable.v1.DeleteTagRequest
import net.twinte.api.timetable.v1.DeleteTagResponse
import net.twinte.api.timetable.v1.ListRegisteredCoursesRequest
import net.twinte.api.timetable.v1.ListRegisteredCoursesResponse
import net.twinte.api.timetable.v1.ListTagsRequest
import net.twinte.api.timetable.v1.ListTagsResponse
import net.twinte.api.timetable.v1.RearrangeTagsRequest
import net.twinte.api.timetable.v1.RearrangeTagsResponse
import net.twinte.api.timetable.v1.ScheduleList
import net.twinte.api.timetable.v1.SearchCoursesRequest
import net.twinte.api.timetable.v1.SearchCoursesResponse
import net.twinte.api.timetable.v1.UpdateRegisteredCourseRequest
import net.twinte.api.timetable.v1.UpdateRegisteredCourseResponse
import net.twinte.api.timetable.v1.UpdateTagRequest
import net.twinte.api.timetable.v1.UpdateTagResponse
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.Course
import net.twinte.mobile_experiments.core.domain.CreateRegisteredCourseInput
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.SearchCoursesInput
import net.twinte.mobile_experiments.core.domain.Tag
import net.twinte.mobile_experiments.core.domain.UpdateRegisteredCourseInput
import pbandk.ExperimentalProtoJson
import pbandk.json.decodeFromJsonString
import pbandk.json.encodeToJsonString

@OptIn(ExperimentalProtoJson::class)
class KtorTimetableApi(
    private val apiBaseUrl: Url = Url("https://app.twinte.net/api/v4"),
    private val httpClient: HttpClient,
) : TimetableApi {
    private val connectTransport = ConnectJsonTransport(apiBaseUrl, httpClient)

    constructor(
        apiBaseUrl: String,
        httpClient: HttpClient,
    ) : this(
        apiBaseUrl = Url(apiBaseUrl.trimEnd('/')),
        httpClient = httpClient,
    )

    override suspend fun searchCourses(input: SearchCoursesInput): List<Course> {
        val body = connectTransport.postPublic(
            service = "timetable.v1.TimetableService",
            method = "SearchCourses",
            requestBody = SearchCoursesRequest(
                year = AcademicYear(input.year),
                keywords = input.keywords,
                codePrefixesIncluded = input.codePrefixesIncluded,
                codePrefixesExcluded = input.codePrefixesExcluded,
                schedulesFullyIncluded = input.schedulesFullyIncluded.map { it.toApiSchedule() },
                schedulesPartiallyOverlapped = input.schedulesPartiallyOverlapped.map { it.toApiSchedule() },
                limit = input.limit,
                offset = input.offset,
            ).encodeToJsonString(),
        )
        return SearchCoursesResponse.decodeFromJsonString(body).courses.map { it.toDomainCourse() }
    }

    override suspend fun listRegisteredCourses(session: TwinteSession, year: Int?): List<RegisteredCourse> {
        val body = postConnectJson(
            session = session,
            method = "ListRegisteredCourses",
            requestBody = ListRegisteredCoursesRequest(
                year = year?.let(::AcademicYear),
            ).encodeToJsonString(),
        )
        return ListRegisteredCoursesResponse.decodeFromJsonString(body)
            .registeredCourses
            .map { it.toDomainRegisteredCourse() }
    }

    override suspend fun listTags(session: TwinteSession): List<Tag> {
        val body = postConnectJson(
            session = session,
            method = "ListTags",
            requestBody = ListTagsRequest().encodeToJsonString(),
        )
        return ListTagsResponse.decodeFromJsonString(body)
            .tags
            .map { it.toDomainTag() }
    }

    override suspend fun createRegisteredCoursesByCodes(
        session: TwinteSession,
        year: Int,
        codes: List<String>,
    ): List<RegisteredCourse> {
        val body = postConnectJson(
            session = session,
            method = "CreateRegisteredCoursesByCodes",
            requestBody = CreateRegisteredCoursesByCodesRequest(
                year = AcademicYear(year),
                codes = codes,
            ).encodeToJsonString(),
        )
        return CreateRegisteredCoursesByCodesResponse.decodeFromJsonString(body)
            .registeredCourses
            .map { it.toDomainRegisteredCourse() }
    }

    override suspend fun createRegisteredCourseManually(
        session: TwinteSession,
        input: CreateRegisteredCourseInput,
    ): RegisteredCourse {
        val body = postConnectJson(
            session = session,
            method = "CreateRegisteredCourseManually",
            requestBody = CreateRegisteredCourseManuallyRequest(
                year = AcademicYear(input.year),
                name = input.name,
                instructors = input.instructors,
                credit = input.credit,
                methods = input.methods.map { it.toApiCourseMethod() },
                schedules = input.schedules.map { it.toApiSchedule() },
            ).encodeToJsonString(),
        )
        return requireNotNull(
            CreateRegisteredCourseManuallyResponse.decodeFromJsonString(body).registeredCourse,
        ) { "CreateRegisteredCourseManuallyResponse.registeredCourse is missing" }
            .toDomainRegisteredCourse()
    }

    override suspend fun updateRegisteredCourse(
        session: TwinteSession,
        input: UpdateRegisteredCourseInput,
    ): RegisteredCourse {
        val body = postConnectJson(
            session = session,
            method = "UpdateRegisteredCourse",
            requestBody = UpdateRegisteredCourseRequest(
                id = UUID(input.id),
                name = input.name,
                instructors = input.instructors,
                credit = input.credit,
                methods = input.methods?.let { CourseMethodList(it.map { method -> method.toApiCourseMethod() }) },
                schedules = input.schedules?.let { ScheduleList(it.map { schedule -> schedule.toApiSchedule() }) },
                memo = input.memo,
                attendance = input.attendance,
                absence = input.absence,
                late = input.late,
                tagIds = input.tagIds?.let { UUIDList(it.map(::UUID)) },
            ).encodeToJsonString(),
        )
        return requireNotNull(
            UpdateRegisteredCourseResponse.decodeFromJsonString(body).registeredCourse,
        ) { "UpdateRegisteredCourseResponse.registeredCourse is missing" }
            .toDomainRegisteredCourse()
    }

    override suspend fun deleteRegisteredCourse(session: TwinteSession, id: String) {
        val body = postConnectJson(
            session = session,
            method = "DeleteRegisteredCourse",
            requestBody = DeleteRegisteredCourseRequest(UUID(id)).encodeToJsonString(),
        )
        DeleteRegisteredCourseResponse.decodeFromJsonString(body)
    }

    override suspend fun createTag(session: TwinteSession, name: String): Tag {
        val body = postConnectJson(
            session = session,
            method = "CreateTag",
            requestBody = CreateTagRequest(name).encodeToJsonString(),
        )
        return requireNotNull(CreateTagResponse.decodeFromJsonString(body).tag) {
            "CreateTagResponse.tag is missing"
        }.toDomainTag()
    }

    override suspend fun updateTag(session: TwinteSession, id: String, name: String): Tag {
        val body = postConnectJson(
            session = session,
            method = "UpdateTag",
            requestBody = UpdateTagRequest(id = UUID(id), name = name).encodeToJsonString(),
        )
        return requireNotNull(UpdateTagResponse.decodeFromJsonString(body).tag) {
            "UpdateTagResponse.tag is missing"
        }.toDomainTag()
    }

    override suspend fun deleteTag(session: TwinteSession, id: String) {
        val body = postConnectJson(
            session = session,
            method = "DeleteTag",
            requestBody = DeleteTagRequest(UUID(id)).encodeToJsonString(),
        )
        DeleteTagResponse.decodeFromJsonString(body)
    }

    override suspend fun rearrangeTags(session: TwinteSession, ids: List<String>): List<Tag> {
        val body = postConnectJson(
            session = session,
            method = "RearrangeTags",
            requestBody = RearrangeTagsRequest(ids.map(::UUID)).encodeToJsonString(),
        )
        return RearrangeTagsResponse.decodeFromJsonString(body).tags.map { it.toDomainTag() }
    }

    private suspend fun postConnectJson(
        session: TwinteSession,
        method: String,
        requestBody: String,
    ): String = connectTransport.postAuthenticated(
        service = "timetable.v1.TimetableService",
        method = method,
        session = session,
        requestBody = requestBody,
    )
}

package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import net.twinte.api.shared.AcademicYear
import net.twinte.api.timetable.v1.ListRegisteredCoursesRequest
import net.twinte.api.timetable.v1.ListRegisteredCoursesResponse
import net.twinte.api.timetable.v1.ListTagsRequest
import net.twinte.api.timetable.v1.ListTagsResponse
import net.twinte.mobile_experiments.core.api.TimetableApi
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.core.domain.Tag
import pbandk.ExperimentalProtoJson
import pbandk.json.decodeFromJsonString
import pbandk.json.encodeToJsonString
import net.twinte.api.timetable.v1.CourseMethod as ApiCourseMethod
import net.twinte.api.timetable.v1.Day as ApiDay
import net.twinte.api.timetable.v1.Module as ApiModule
import net.twinte.api.timetable.v1.RegisteredCourse as ApiRegisteredCourse
import net.twinte.api.timetable.v1.Schedule as ApiSchedule
import net.twinte.api.timetable.v1.Tag as ApiTag

@OptIn(ExperimentalProtoJson::class)
class KtorTimetableApi(
    private val apiBaseUrl: Url = Url("https://app.twinte.net/api/v4"),
    private val httpClient: HttpClient,
) : TimetableApi {
    constructor(
        apiBaseUrl: String,
        httpClient: HttpClient,
    ) : this(
        apiBaseUrl = Url(apiBaseUrl.trimEnd('/')),
        httpClient = httpClient,
    )

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

    private suspend fun postConnectJson(
        session: TwinteSession,
        method: String,
        requestBody: String,
    ): String {
        val response = mapKtorNetworkErrors {
            httpClient.post(connectUrl("timetable.v1.TimetableService", method)) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header("Connect-Protocol-Version", "1")
                header(HttpHeaders.Cookie, "${session.cookieName}=${session.sessionId}")
                setBody(requestBody)
            }
        }
        val body = response.body<String>()
        if (!response.status.isSuccess()) {
            throw TwinteApiException(response.status.value, body)
        }
        return body
    }

    private fun connectUrl(service: String, method: String): Url =
        URLBuilder(apiBaseUrl).apply {
            appendPathSegments(service, method)
        }.build()
}

internal fun ApiRegisteredCourse.toDomainRegisteredCourse(): RegisteredCourse =
    RegisteredCourse(
        id = requireNotNull(id) { "RegisteredCourse.id is missing" }.value,
        year = requireNotNull(year) { "RegisteredCourse.year is missing" }.value,
        name = name,
        instructors = instructors,
        credit = credit,
        methods = methods.map { it.toDomainCourseMethod() },
        schedules = schedules.map { it.toDomainCourseSchedule() },
        memo = memo,
        attendance = attendance,
        absence = absence,
        late = late,
        tagIds = tagIds.map { it.value },
        code = code,
    )

private fun ApiSchedule.toDomainCourseSchedule(): CourseSchedule =
    CourseSchedule(
        module = module.toDomainModule(),
        day = day.toDomainDay(),
        period = period,
        locations = locations,
    )

private fun ApiTag.toDomainTag(): Tag =
    Tag(
        id = requireNotNull(id) { "Tag.id is missing" }.value,
        name = name,
        order = order,
    )

private fun ApiModule.toDomainModule(): Module =
    when (this) {
        ApiModule.SPRING_A -> Module.SpringA
        ApiModule.SPRING_B -> Module.SpringB
        ApiModule.SPRING_C -> Module.SpringC
        ApiModule.FALL_A -> Module.FallA
        ApiModule.FALL_B -> Module.FallB
        ApiModule.FALL_C -> Module.FallC
        ApiModule.SUMMER_VACATION -> Module.SummerVacation
        ApiModule.SPRING_VACATION -> Module.SpringVacation
        else -> Module.Unknown
    }

private fun ApiDay.toDomainDay(): Day =
    when (this) {
        ApiDay.SUN -> Day.Sunday
        ApiDay.MON -> Day.Monday
        ApiDay.TUE -> Day.Tuesday
        ApiDay.WED -> Day.Wednesday
        ApiDay.THU -> Day.Thursday
        ApiDay.FRI -> Day.Friday
        ApiDay.SAT -> Day.Saturday
        ApiDay.INTENSIVE -> Day.Intensive
        ApiDay.APPOINTMENT -> Day.Appointment
        ApiDay.ANY_TIME -> Day.AnyTime
        ApiDay.NT -> Day.Nt
        else -> error("Unsupported timetable day: $this")
    }

private fun ApiCourseMethod.toDomainCourseMethod(): CourseMethod =
    when (this) {
        ApiCourseMethod.ONLINE_ASYNCHRONOUS -> CourseMethod.OnlineAsynchronous
        ApiCourseMethod.ONLINE_SYNCHRONOUS -> CourseMethod.OnlineSynchronous
        ApiCourseMethod.FACE_TO_FACE -> CourseMethod.FaceToFace
        ApiCourseMethod.OTHERS -> CourseMethod.Others
        else -> error("Unsupported course method: $this")
    }

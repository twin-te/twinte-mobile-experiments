package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.datetime.LocalDate
import net.twinte.api.schoolcalendar.v1.Event as ApiEvent
import net.twinte.api.schoolcalendar.v1.EventType as ApiEventType
import net.twinte.api.schoolcalendar.v1.Module as ApiModule
import net.twinte.api.shared.RFC3339FullDate
import net.twinte.api.shared.Weekday as ApiWeekday
import net.twinte.api.unified.v1.GetByDateRequest
import net.twinte.api.unified.v1.GetByDateResponse
import net.twinte.mobile_experiments.core.api.DailyApiSnapshot
import net.twinte.mobile_experiments.core.api.DailyTimetableApi
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.SchoolCalendarEvent
import net.twinte.mobile_experiments.core.domain.toDailyCourse
import pbandk.ExperimentalProtoJson
import pbandk.json.decodeFromJsonString
import pbandk.json.encodeToJsonString

@OptIn(ExperimentalProtoJson::class)
class KtorUnifiedApi(
    private val apiBaseUrl: Url = Url("https://app.twinte.net/api/v4"),
    private val httpClient: HttpClient,
) : DailyTimetableApi {
    private val connectTransport = ConnectJsonTransport(apiBaseUrl, httpClient)

    constructor(
        apiBaseUrl: String,
        httpClient: HttpClient,
    ) : this(
        apiBaseUrl = Url(apiBaseUrl.trimEnd('/')),
        httpClient = httpClient,
    )

    override suspend fun getByDate(session: TwinteSession, date: LocalDate): DailyApiSnapshot {
        val body = connectTransport.post(
            service = "unified.v1.UnifiedService",
            method = "GetByDate",
            session = session,
            requestBody = GetByDateRequest(
                date = RFC3339FullDate(date.toString()),
            ).encodeToJsonString(),
        )
        return GetByDateResponse.decodeFromJsonString(body).toDomainSnapshot(date)
    }
}

internal fun GetByDateResponse.toDomainSnapshot(date: LocalDate): DailyApiSnapshot =
    DailyApiSnapshot(
        date = date,
        module = module.toDomainModule(),
        events = events.map(ApiEvent::toDomainEvent),
        courses = registeredCourses.map { it.toDomainRegisteredCourse().toDailyCourse() },
    )

private fun ApiEvent.toDomainEvent(): SchoolCalendarEvent =
    SchoolCalendarEvent(
        id = id,
        type = type.toDomainEventType(),
        date = LocalDate.parse(requireNotNull(date) { "Event.date is missing" }.value),
        description = description,
        changeTo = changeTo?.toDomainDay(),
    )

private fun ApiEventType.toDomainEventType(): EventType =
    when (this) {
        ApiEventType.HOLIDAY -> EventType.Holiday
        ApiEventType.PUBLIC_HOLIDAY -> EventType.PublicHoliday
        ApiEventType.EXAM -> EventType.Exam
        ApiEventType.SUBSTITUTE_DAY -> EventType.SubstituteDay
        ApiEventType.OTHER -> EventType.Other
        else -> error("Unsupported event type: $this")
    }

private fun ApiModule.toDomainModule(): Module =
    when (this) {
        ApiModule.SPRING_A -> Module.SpringA
        ApiModule.SPRING_B -> Module.SpringB
        ApiModule.SPRING_C -> Module.SpringC
        ApiModule.SUMMER_VACATION -> Module.SummerVacation
        ApiModule.FALL_A -> Module.FallA
        ApiModule.FALL_B -> Module.FallB
        ApiModule.WINTER_VACATION -> Module.WinterVacation
        ApiModule.FALL_C -> Module.FallC
        ApiModule.SPRING_VACATION -> Module.SpringVacation
        else -> Module.Unknown
    }

private fun ApiWeekday.toDomainDay(): Day =
    when (this) {
        ApiWeekday.SUNDAY -> Day.Sunday
        ApiWeekday.MONDAY -> Day.Monday
        ApiWeekday.TUESDAY -> Day.Tuesday
        ApiWeekday.WEDNESDAY -> Day.Wednesday
        ApiWeekday.THURSDAY -> Day.Thursday
        ApiWeekday.FRIDAY -> Day.Friday
        ApiWeekday.SATURDAY -> Day.Saturday
        else -> error("Unsupported weekday: $this")
    }

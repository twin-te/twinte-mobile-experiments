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
import kotlinx.datetime.LocalDate
import net.twinte.api.schoolcalendar.v1.Event
import net.twinte.api.schoolcalendar.v1.EventType
import net.twinte.api.schoolcalendar.v1.Module as ApiModule
import net.twinte.api.shared.RFC3339FullDate
import net.twinte.api.shared.Weekday
import net.twinte.api.unified.v1.GetByDateResponse
import net.twinte.mobile_experiments.core.api.TwinteApiException
import net.twinte.mobile_experiments.core.auth.TwinteSession
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType as DomainEventType
import net.twinte.mobile_experiments.core.domain.Module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import pbandk.ExperimentalProtoJson
import pbandk.json.encodeToJsonString

@OptIn(ExperimentalProtoJson::class)
class KtorUnifiedApiTest {
    @Test
    fun getByDatePostsGeneratedRequestToConnectEndpoint() = runTest {
        val date = LocalDate(2026, 5, 1)
        val engine = MockEngine { request ->
            assertEquals(
                "https://app.twinte.net/api/v4/unified.v1.UnifiedService/GetByDate",
                request.url.toString(),
            )
            assertEquals("twinte_session=session-id", request.headers[HttpHeaders.Cookie])
            assertEquals("1", request.headers[ConnectProtocolVersionHeader])
            assertTrue((request.body as TextContent).text.contains(date.toString()))
            respond(
                content = GetByDateResponse(module = ApiModule.SPRING_A).encodeToJsonString(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorUnifiedApi(httpClient = HttpClient(engine))

        val snapshot = api.getByDate(TwinteSession("session-id"), date)

        assertEquals(date, snapshot.date)
        assertEquals(Module.SpringA, snapshot.module)
    }

    @Test
    fun getByDatePreservesConnectErrorResponse() = runTest {
        val engine = MockEngine {
            respond(
                content = "server error",
                status = HttpStatusCode.InternalServerError,
            )
        }
        val api = KtorUnifiedApi(httpClient = HttpClient(engine))

        val error = assertFailsWith<TwinteApiException> {
            api.getByDate(TwinteSession("session-id"), LocalDate(2026, 5, 1))
        }

        assertEquals(HttpStatusCode.InternalServerError.value, error.statusCode)
        assertEquals("server error", error.responseBody)
    }

    @Test
    fun mapsGeneratedDailyResponseToDomain() {
        val date = LocalDate(2026, 5, 1)
        val snapshot = GetByDateResponse(
            module = ApiModule.SPRING_A,
            events = listOf(
                Event(
                    id = 1,
                    type = EventType.SUBSTITUTE_DAY,
                    date = RFC3339FullDate(date.toString()),
                    description = "Monday classes",
                    changeTo = Weekday.MONDAY,
                ),
            ),
        ).toDomainSnapshot(date)

        assertEquals(date, snapshot.date)
        assertEquals(Module.SpringA, snapshot.module)
        assertEquals(DomainEventType.SubstituteDay, snapshot.events.single().type)
        assertEquals(Day.Monday, snapshot.events.single().changeTo)
    }
}

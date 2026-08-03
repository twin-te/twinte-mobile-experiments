package net.twinte.mobile_experiments.core.api.ktor

import kotlinx.datetime.LocalDate
import net.twinte.api.schoolcalendar.v1.Event
import net.twinte.api.schoolcalendar.v1.EventType
import net.twinte.api.schoolcalendar.v1.Module as ApiModule
import net.twinte.api.shared.RFC3339FullDate
import net.twinte.api.shared.Weekday
import net.twinte.api.unified.v1.GetByDateResponse
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.EventType as DomainEventType
import net.twinte.mobile_experiments.core.domain.Module
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorUnifiedApiTest {
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

package net.twinte.mobile_experiments

import io.ktor.http.Url
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import net.twinte.mobile_experiments.core.domain.RegisteredCourse
import net.twinte.mobile_experiments.feature.course.courseMapUrl
import net.twinte.mobile_experiments.feature.course.responUrl
import net.twinte.mobile_experiments.feature.course.syllabusUrl
import net.twinte.mobile_experiments.ui.browser.browserUrlOrNull
import net.twinte.mobile_experiments.ui.browser.isMapUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseLinksTest {
    @Test
    fun syllabusUsesTheCourseYearAndEncodesItsCodeAsOnePathSegment() {
        assertEquals("https://kdb.tsukuba.ac.jp/syllabi/2026/GB12345/jpn/", syllabusUrl(course)?.toString())
        assertEquals("/syllabi/2026/A%2FB%3F/jpn/", syllabusUrl(course.copy(code = "A/B?"))?.encodedPath)
        assertNull(syllabusUrl(course.copy(code = null)))
        assertNull(syllabusUrl(course.copy(code = " ")))
    }

    @Test
    fun mapQueryPreservesJapaneseAndSpecialCharactersWithoutDuplicatingLocations() {
        val schedule = CourseSchedule(Module.FallB, Day.Monday, 2, "3A204 & A/B")
        val map = courseMapUrl(course.copy(schedules = listOf(schedule, schedule.copy(module = Module.SpringA))))
        assertEquals("www.google.com", map.host)
        assertEquals("1", map.parameters["api"])
        assertEquals("筑波大学 3A204 & A/B", map.parameters["query"])
        assertTrue(isMapUrl(map))
        assertEquals("筑波大学", courseMapUrl(course).parameters["query"])
        assertEquals("https://atmnb.tsukuba.ac.jp", responUrl.toString())
    }

    @Test
    fun browserAcceptsOnlyHttpsWithoutEmbeddedCredentialsAndMatchesTheExactMapsHostAndPath() {
        assertEquals(Url("https://kdb.tsukuba.ac.jp/"), browserUrlOrNull("https://kdb.tsukuba.ac.jp/"))
        listOf("http://example.com", "file:///private/data", "content://data", "javascript:alert(1)", "data:text/html,Hi",
            "https://user:password@example.com/", "not a URL").forEach {
            assertNull(browserUrlOrNull(it), it)
        }
        assertFalse(isMapUrl(Url("https://www.google.com.evil.example/maps/")))
        assertFalse(isMapUrl(Url("https://www.google.com/maps-evil/")))
        assertFalse(isMapUrl(Url("http://www.google.com/maps/")))
    }

    private val course = RegisteredCourse("course", 2026, "Seminar", "", "1.5", emptyList(), emptyList(), code = "GB12345")
}

package net.twinte.mobile_experiments.feature.course

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import net.twinte.mobile_experiments.core.domain.RegisteredCourse

internal fun syllabusUrl(course: RegisteredCourse): Url? = course.code?.takeIf(String::isNotBlank)?.let { code ->
    URLBuilder("https://kdb.tsukuba.ac.jp").apply {
        appendPathSegments("syllabi", course.year.toString(), code, "jpn", "", encodeSlash = true)
    }.build()
}

internal val responUrl = Url("https://atmnb.tsukuba.ac.jp")

internal fun courseMapUrl(course: RegisteredCourse): Url = URLBuilder("https://www.google.com/maps/search/").apply {
    parameters.append("api", "1")
    val locations = course.schedules.map { it.locations }.filter(String::isNotBlank).distinct().joinToString(" / ")
    parameters.append("query", listOf("筑波大学", locations).filter(String::isNotBlank).joinToString(" "))
}.build()

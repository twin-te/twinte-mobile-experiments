package net.twinte.mobile_experiments.core.api

import io.ktor.http.HttpStatusCode

class TwinteApiException(
    val status: HttpStatusCode,
    val responseBody: String,
) : RuntimeException("Twinte API request failed: $status $responseBody")

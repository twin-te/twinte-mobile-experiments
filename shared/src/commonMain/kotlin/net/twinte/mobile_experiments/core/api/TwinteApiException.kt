package net.twinte.mobile_experiments.core.api

class TwinteApiException(
    val statusCode: Int,
    val responseBody: String,
) : RuntimeException("Twinte API request failed: $statusCode $responseBody") {
    val isUnauthorized: Boolean
        get() = statusCode == UnauthorizedStatusCode

    private companion object {
        const val UnauthorizedStatusCode = 401
    }
}

class TwinteNetworkException(cause: Throwable) : RuntimeException("Twinte API network request failed", cause)

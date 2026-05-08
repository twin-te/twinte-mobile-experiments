package net.twinte.mobile_experiments.core.api.grpc

import net.twinte.mobile_experiments.core.api.AuthApi

data class GrpcEndpoint(
    val host: String,
    val port: Int = 443,
    val usePlaintext: Boolean = false,
    val sessionId: String? = null,
    val sessionCookieName: String = "twinte_session",
)

expect fun createGrpcAuthApi(endpoint: GrpcEndpoint): AuthApi

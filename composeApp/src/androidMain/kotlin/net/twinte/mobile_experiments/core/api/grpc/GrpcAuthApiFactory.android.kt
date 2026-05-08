package net.twinte.mobile_experiments.core.api.grpc

import io.grpc.Metadata
import kotlinx.rpc.grpc.GrpcMetadata
import kotlinx.rpc.grpc.append
import kotlinx.rpc.grpc.client.GrpcCallCredentials
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.grpc.client.plus
import kotlinx.rpc.withService
import net.twinte.api.auth.v1.AuthService
import net.twinte.mobile_experiments.core.api.AuthApi

actual fun createGrpcAuthApi(endpoint: GrpcEndpoint): AuthApi =
    GrpcAuthApi(
        service = GrpcClient(endpoint.host, endpoint.port) {
            val channelCredentials = if (endpoint.usePlaintext) {
                plaintext()
            } else {
                tls {}
            }
            credentials = endpoint.sessionId
                ?.let { channelCredentials + SessionCookieCallCredentials(endpoint.sessionCookieName, it) }
                ?: channelCredentials
        }.withService<AuthService>(),
    )

private class SessionCookieCallCredentials(
    private val cookieName: String,
    private val sessionId: String,
) : GrpcCallCredentials {
    override val requiresTransportSecurity: Boolean = false

    override suspend fun GrpcCallCredentials.Context.getRequestMetadata(): Metadata =
        GrpcMetadata {
            append("cookie", "$cookieName=$sessionId")
        }
}

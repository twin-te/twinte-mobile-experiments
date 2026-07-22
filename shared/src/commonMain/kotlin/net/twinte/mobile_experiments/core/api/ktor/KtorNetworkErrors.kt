package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import net.twinte.mobile_experiments.core.api.TwinteNetworkException

internal suspend fun <T> mapKtorNetworkErrors(block: suspend () -> T): T =
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: HttpRequestTimeoutException) {
        throw TwinteNetworkException(error)
    } catch (error: ConnectTimeoutException) {
        throw TwinteNetworkException(error)
    } catch (error: SocketTimeoutException) {
        throw TwinteNetworkException(error)
    }

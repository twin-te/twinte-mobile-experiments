package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import net.twinte.mobile_experiments.core.api.TwinteNetworkException

internal suspend fun <T> mapKtorNetworkErrors(block: suspend () -> T): T =
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        throw TwinteNetworkException(error)
    } catch (error: UnresolvedAddressException) {
        throw TwinteNetworkException(error)
    }

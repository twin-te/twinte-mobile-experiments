package net.twinte.mobile_experiments.core.api.ktor

import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import net.twinte.mobile_experiments.core.api.TwinteNetworkException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KtorNetworkErrorsTest {
    @Test
    fun mapsIoExceptionToNetworkException() = runTest {
        val error = assertFailsWith<TwinteNetworkException> {
            mapKtorNetworkErrors {
                throw IOException("offline")
            }
        }

        assertIs<IOException>(error.cause)
    }

    @Test
    fun mapsUnresolvedAddressToNetworkException() = runTest {
        val error = assertFailsWith<TwinteNetworkException> {
            mapKtorNetworkErrors {
                throw UnresolvedAddressException()
            }
        }

        assertIs<UnresolvedAddressException>(error.cause)
    }
}

package net.twinte.mobile_experiments.core.api.kotlinxrpc

import net.twinte.api.auth.v1.AuthService
import net.twinte.api.auth.v1.Provider
import net.twinte.api.auth.v1.User
import net.twinte.api.auth.v1.invoke
import net.twinte.api.shared.UUID
import net.twinte.api.shared.invoke
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinxRpcGeneratedApiTest {
    @Test
    fun generatedMessagesAreVisibleFromCommonCode() {
        val user = User {
            id = UUID {
                value = "user-id"
            }
        }

        assertEquals("user-id", user.id.value)
        assertEquals(Provider.PROVIDER_GOOGLE, Provider.entries[1])
    }

    @Test
    fun generatedServiceInterfaceIsVisibleFromCommonCode() {
        acceptsGeneratedServiceType(null)
    }

    private fun acceptsGeneratedServiceType(@Suppress("UNUSED_PARAMETER") service: AuthService?) {
    }
}

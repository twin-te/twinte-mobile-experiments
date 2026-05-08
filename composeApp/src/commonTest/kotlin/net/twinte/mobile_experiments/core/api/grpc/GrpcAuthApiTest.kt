package net.twinte.mobile_experiments.core.api.grpc

import net.twinte.api.auth.v1.AuthService
import net.twinte.api.auth.v1.DeleteAccountRequest
import net.twinte.api.auth.v1.DeleteAccountResponse
import net.twinte.api.auth.v1.DeleteUserAuthenticationRequest
import net.twinte.api.auth.v1.DeleteUserAuthenticationResponse
import net.twinte.api.auth.v1.GetMeRequest
import net.twinte.api.auth.v1.GetMeResponse
import net.twinte.api.auth.v1.Provider
import net.twinte.api.auth.v1.User
import net.twinte.api.auth.v1.UserAuthentication
import net.twinte.api.auth.v1.invoke
import net.twinte.api.shared.UUID
import net.twinte.api.shared.invoke
import net.twinte.mobile_experiments.core.domain.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class GrpcAuthApiTest {
    @Test
    fun mapsGeneratedAuthUserToDomainUser() = kotlinx.coroutines.test.runTest {
        val api = GrpcAuthApi(FakeAuthService())

        val user = api.getMe()

        assertEquals("user-id", user.id)
        assertEquals(AuthProvider.Google, user.authentications.single().provider)
        assertEquals("google-id", user.authentications.single().socialId)
    }

    private class FakeAuthService : AuthService {
        override suspend fun GetMe(message: GetMeRequest): GetMeResponse =
            GetMeResponse {
                user = User {
                    id = UUID { value = "user-id" }
                    authentications = listOf(
                        UserAuthentication {
                            provider = Provider.PROVIDER_GOOGLE
                            socialId = "google-id"
                        },
                    )
                }
            }

        override suspend fun DeleteUserAuthentication(
            message: DeleteUserAuthenticationRequest,
        ): DeleteUserAuthenticationResponse =
            DeleteUserAuthenticationResponse {}

        override suspend fun DeleteAccount(message: DeleteAccountRequest): DeleteAccountResponse =
            DeleteAccountResponse {}
    }
}

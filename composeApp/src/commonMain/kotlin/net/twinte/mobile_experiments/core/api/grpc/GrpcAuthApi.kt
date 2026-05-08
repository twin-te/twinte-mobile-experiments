package net.twinte.mobile_experiments.core.api.grpc

import net.twinte.api.auth.v1.AuthService
import net.twinte.api.auth.v1.DeleteAccountRequest
import net.twinte.api.auth.v1.DeleteUserAuthenticationRequest
import net.twinte.api.auth.v1.GetMeRequest
import net.twinte.api.auth.v1.Provider
import net.twinte.api.auth.v1.invoke
import net.twinte.mobile_experiments.core.api.AuthApi
import net.twinte.mobile_experiments.core.domain.AuthProvider
import net.twinte.mobile_experiments.core.domain.User
import net.twinte.mobile_experiments.core.domain.UserAuthentication
import net.twinte.api.auth.v1.User as ApiUser
import net.twinte.api.auth.v1.UserAuthentication as ApiUserAuthentication

class GrpcAuthApi(
    private val service: AuthService,
) : AuthApi {
    override suspend fun getMe(): User =
        service.GetMe(GetMeRequest {}).user.toDomain()

    override suspend fun deleteAccount() {
        service.DeleteAccount(DeleteAccountRequest {})
    }

    override suspend fun deleteUserAuthentication(provider: AuthProvider) {
        service.DeleteUserAuthentication(
            DeleteUserAuthenticationRequest {
                this.provider = provider.toApi()
            },
        )
    }
}

private fun ApiUser.toDomain(): User =
    User(
        id = id.value,
        authentications = authentications.map { it.toDomain() },
    )

private fun ApiUserAuthentication.toDomain(): UserAuthentication =
    UserAuthentication(
        provider = provider.toDomain(),
        socialId = socialId,
    )

private fun AuthProvider.toApi(): Provider =
    when (this) {
        AuthProvider.Google -> Provider.PROVIDER_GOOGLE
        AuthProvider.Apple -> Provider.PROVIDER_APPLE
        AuthProvider.Twitter -> Provider.PROVIDER_TWITTER
    }

private fun Provider.toDomain(): AuthProvider =
    when (this) {
        Provider.PROVIDER_GOOGLE -> AuthProvider.Google
        Provider.PROVIDER_APPLE -> AuthProvider.Apple
        Provider.PROVIDER_TWITTER -> AuthProvider.Twitter
        Provider.PROVIDER_UNSPECIFIED,
        is Provider.UNRECOGNIZED,
        -> error("Unsupported authentication provider: $this")
    }

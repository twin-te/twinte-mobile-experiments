package net.twinte.mobile_experiments.core.domain

data class User(
    val id: String,
    val authentications: List<UserAuthentication> = emptyList(),
)

data class UserAuthentication(
    val provider: AuthProvider,
    val socialId: String,
)

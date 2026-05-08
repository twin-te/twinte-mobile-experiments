package net.twinte.mobile_experiments.core.domain

data class User(
    val id: String,
    val authentications: List<UserAuthentication>,
)

data class UserAuthentication(
    val provider: AuthProvider,
    val socialId: String,
)

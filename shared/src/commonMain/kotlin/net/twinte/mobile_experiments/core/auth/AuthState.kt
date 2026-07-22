package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.User

sealed interface AuthState {
    data object Unknown : AuthState

    data object LoggedOut : AuthState

    data class LoggedIn(val session: AuthSession) : AuthState {
        val user: User
            get() = session.user
    }
}

sealed interface AuthFailure {
    data object Unauthenticated : AuthFailure

    data object Canceled : AuthFailure

    data object CredentialUnavailable : AuthFailure

    data class Network(val cause: Throwable? = null) : AuthFailure

    data class Unexpected(val cause: Throwable? = null) : AuthFailure
}

class AuthCanceledException : Exception()

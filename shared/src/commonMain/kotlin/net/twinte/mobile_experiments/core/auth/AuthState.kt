package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.User

sealed interface AuthState {
    data object Unknown : AuthState

    data object LoggedOut : AuthState

    data class LoggedIn(val user: User) : AuthState
}

sealed class AuthFailure(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object Unauthenticated : AuthFailure()

    data object Canceled : AuthFailure()

    class Network(cause: Throwable? = null) : AuthFailure(cause = cause)

    class Unexpected(cause: Throwable? = null) : AuthFailure(cause = cause)
}

class AuthCanceledException : Exception()

package net.twinte.mobile_experiments.core.auth

import net.twinte.mobile_experiments.core.domain.AuthProvider

class AuthUseCase(
    private val authRepository: AuthRepository,
) {
    suspend fun restoreSession(): AuthState =
        authRepository.getMe().fold(
            onSuccess = { AuthState.LoggedIn(it) },
            onFailure = { AuthState.LoggedOut },
        )

    suspend fun signIn(provider: AuthProvider): AuthState {
        authRepository.signIn(provider).getOrThrow()
        return restoreSession()
    }

    suspend fun signOut(): AuthState {
        authRepository.signOut().getOrThrow()
        return AuthState.LoggedOut
    }

    suspend fun deleteAccount(): AuthState {
        authRepository.deleteAccount().getOrThrow()
        return AuthState.LoggedOut
    }
}

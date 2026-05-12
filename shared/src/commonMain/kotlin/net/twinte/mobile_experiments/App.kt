package net.twinte.mobile_experiments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import net.twinte.mobile_experiments.core.api.ktor.KtorApiException
import net.twinte.mobile_experiments.core.api.ktor.KtorAuthApi
import net.twinte.mobile_experiments.core.api.ktor.TwinteSession
import net.twinte.mobile_experiments.core.domain.User

interface GoogleIdTokenProvider {
    val isConfigured: Boolean

    suspend fun requestIdToken(): String?
}

object UnavailableGoogleIdTokenProvider : GoogleIdTokenProvider {
    override val isConfigured: Boolean = false

    override suspend fun requestIdToken(): String? = null
}

private const val AppBaseUrl = "https://app.twinte.net"
private const val ApiBaseUrl = "$AppBaseUrl/api/v4"
private const val SessionCookieName = "twinte_session"

@Composable
@Preview
fun App(
    googleIdTokenProvider: GoogleIdTokenProvider = UnavailableGoogleIdTokenProvider,
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            GoogleLoginScreen(googleIdTokenProvider)
        }
    }
}

@Composable
private fun GoogleLoginScreen(
    googleIdTokenProvider: GoogleIdTokenProvider,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<User?>(null) }
    var message by remember {
        mutableStateOf(
            if (googleIdTokenProvider.isConfigured) {
                "Not signed in"
            } else {
                "Google client ID is not configured"
            },
        )
    }

    fun refreshMe(currentSessionId: String?) {
        if (currentSessionId == null) {
            user = null
            message = "No session"
            return
        }
        scope.launch {
            isLoading = true
            message = "Checking session..."
            runCatching {
                KtorAuthApi(
                    apiBaseUrl = ApiBaseUrl,
                    session = TwinteSession(currentSessionId),
                ).getMe()
            }.onSuccess {
                user = it
                message = "Signed in"
            }.onFailure {
                user = null
                message = it.toStatusMessage()
            }
            isLoading = false
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            isLoading = true
            message = "Opening Google..."
            runCatching {
                googleIdTokenProvider.requestIdToken() ?: error("Google ID token is missing")
            }.mapCatching { idToken ->
                message = "Creating session..."
                fetchSessionIdWithGoogleIdToken(idToken)
            }.onSuccess { foundSessionId ->
                sessionId = foundSessionId
                refreshMe(foundSessionId)
            }.onFailure {
                user = null
                message = it.toStatusMessage()
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Twin:te Auth", style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        sessionId?.let {
            Text("Session: ${it.take(8)}...", style = MaterialTheme.typography.bodyMedium)
        }
        user?.let {
            Text("User ID: ${it.id}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Providers: ${it.authentications.joinToString { auth -> auth.provider.name }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = googleIdTokenProvider.isConfigured && !isLoading,
                onClick = ::signInWithGoogle,
            ) {
                Text("Google")
            }
            OutlinedButton(
                enabled = !isLoading,
                onClick = { refreshMe(sessionId) },
            ) {
                Text("getMe")
            }
            OutlinedButton(
                enabled = !isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                onClick = {
                    sessionId = null
                    user = null
                    message = "Signed out locally"
                },
            ) {
                Text("Clear")
            }
        }
    }
}

private suspend fun fetchSessionIdWithGoogleIdToken(idToken: String): String {
    val client = HttpClient {
        followRedirects = false
    }
    return try {
        val response = client.get("$AppBaseUrl/auth/v4/google/idToken") {
            parameter("token", idToken)
            parameter("redirect_url", AppBaseUrl)
        }
        response.sessionId()
            ?: error("Session cookie is missing: HTTP ${response.status}")
    } finally {
        client.close()
    }
}

private fun HttpResponse.sessionId(): String? =
    headers.getAll(HttpHeaders.SetCookie)
        .orEmpty()
        .firstNotNullOfOrNull(::parseSessionCookie)

private fun parseSessionCookie(setCookie: String): String? =
    setCookie
        .split(';')
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.startsWith("$SessionCookieName=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

private fun Throwable.toStatusMessage(): String =
    when (this) {
        is KtorApiException -> when (status) {
            HttpStatusCode.Unauthorized -> "Session is not authorized"
            else -> "API error: $status"
        }
        else -> message ?: "Unknown error"
    }

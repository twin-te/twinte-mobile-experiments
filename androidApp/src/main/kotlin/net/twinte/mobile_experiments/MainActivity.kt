package net.twinte.mobile_experiments

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(
                googleIdTokenProvider = AndroidGoogleIdTokenProvider(
                    activity = this,
                    serverClientId = BuildConfig.TWINTE_GOOGLE_SERVER_CLIENT_ID,
                ),
                appBaseUrl = BuildConfig.TWINTE_APP_BASE_URL,
            )
        }
    }
}

private class AndroidGoogleIdTokenProvider(
    private val activity: Activity,
    private val serverClientId: String,
) : GoogleIdTokenProvider {
    override val isConfigured: Boolean
        get() = serverClientId.isNotBlank()

    override suspend fun requestIdToken(nonce: String): String? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(serverClientId)
                    .setNonce(nonce)
                    .build(),
            )
            .build()

        return runCatching {
            CredentialManager.create(activity).getCredential(activity, request)
        }.getOrElse { error ->
            when (error) {
                is GetCredentialCancellationException,
                is NoCredentialException,
                -> return null
                is GetCredentialException -> throw error
            }
            throw error
        }.extractGoogleIdToken()
    }
}

private fun GetCredentialResponse.extractGoogleIdToken(): String? {
    val customCredential = credential as? CustomCredential ?: return null
    if (
        customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL &&
        customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
    ) {
        return null
    }

    return GoogleIdTokenCredential.createFrom(customCredential.data).idToken
}

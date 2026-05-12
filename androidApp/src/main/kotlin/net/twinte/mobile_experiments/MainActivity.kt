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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

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

    override suspend fun requestIdToken(): String? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(serverClientId).build(),
            )
            .build()

        return runCatching {
            CredentialManager.create(activity).getCredential(activity, request)
        }.getOrElse { error ->
            if (error is GetCredentialException) {
                return null
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

    return runCatching {
        GoogleIdTokenCredential.createFrom(customCredential.data).idToken
    }.getOrElse { error ->
        if (error is GoogleIdTokenParsingException) {
            return null
        }
        throw error
    }
}

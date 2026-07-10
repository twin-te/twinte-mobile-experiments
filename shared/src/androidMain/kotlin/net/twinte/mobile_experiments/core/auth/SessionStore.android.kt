package net.twinte.mobile_experiments.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberSessionStore(): SessionStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        SecureKeyValueStoreSessionStore(
            AndroidSecureKeyValueStore(context),
        )
    }
}

private class AndroidSecureKeyValueStore(
    context: Context,
) : SecureKeyValueStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) {
            preferences.getString(key, null)
                ?.let(::decrypt)
        }

    override suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            preferences.edit(commit = true) {
                putString(key, encrypt(value))
            }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            preferences.edit(commit = true) {
                remove(key)
            }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encryptedBytes = cipher.doFinal(value.encodeToByteArray())
        return "${cipher.iv.base64()}:${encryptedBytes.base64()}"
    }

    private fun decrypt(value: String): String? {
        val (encodedIv, encodedCipherText) = value.split(':', limit = 2).takeIf { it.size == 2 }
            ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, encodedIv.base64Bytes()))
            cipher.doFinal(encodedCipherText.base64Bytes()).decodeToString()
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_NAME).apply {
            load(null)
        }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE_NAME)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE_NAME = "AndroidKeyStore"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_ALIAS = "net.twinte.mobile_experiments.session_store"
        const val PREFERENCES_NAME = "twinte_auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private fun ByteArray.base64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.base64Bytes(): ByteArray =
    Base64.decode(this, Base64.NO_WRAP)

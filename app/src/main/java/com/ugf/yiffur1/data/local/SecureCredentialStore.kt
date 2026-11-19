package com.ugf.yiffur1.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredCredentials(
    val username: String,
    val apiKey: String
)

/**
 * Hardware-backed Android Keystore AES-256-GCM credential vault for e621 username & API key.
 * Replaces legacy plaintext SharedPreferences storage with modern authenticated encryption.
 */
class SecureCredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _credentials = MutableStateFlow(loadInternal())
    val credentialsFlow: StateFlow<StoredCredentials?> = _credentials.asStateFlow()

    val currentCredentials: StoredCredentials?
        get() = _credentials.value

    val isLoggedIn: Boolean
        get() = _credentials.value != null

    fun save(username: String, apiKey: String) {
        val cleanUser = username.trim()
        val cleanKey = apiKey.trim()
        if (cleanUser.isEmpty() || cleanKey.isEmpty()) return

        val encryptedKey = encrypt(cleanKey)
        prefs.edit()
            .putString(KEY_USERNAME, cleanUser)
            .putString(KEY_API_KEY_ENC, encryptedKey)
            .apply()
        _credentials.value = StoredCredentials(cleanUser, cleanKey)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_API_KEY_ENC)
            .apply()
        _credentials.value = null
    }

    private fun loadInternal(): StoredCredentials? {
        val username = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val encKey = prefs.getString(KEY_API_KEY_ENC, null).orEmpty()
        if (username.isEmpty() || encKey.isEmpty()) return null
        val decrypted = runCatching { decrypt(encKey) }.getOrNull().orEmpty()
        return if (decrypted.isNotEmpty()) StoredCredentials(username, decrypted) else null
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted payload" }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val PREFS_NAME = "yiffur_secure_vault"
        private const val KEY_USERNAME = "username"
        private const val KEY_API_KEY_ENC = "api_key_gcm"
        private const val KEY_ALIAS = "yiffur_e621_credentials_aes256"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}

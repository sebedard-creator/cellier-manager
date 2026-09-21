package com.cellier.manager.sommelier

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SommelierConfiguration(val apiKey: String, val model: String)

/** Stocke la clé choisie par l'utilisateur chiffrée par Android Keystore. */
class SommelierStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): SommelierConfiguration? {
        val encrypted = preferences.getString(KEY_API_KEY, null) ?: return null
        return runCatching {
            SommelierConfiguration(
                apiKey = decrypt(encrypted),
                model = preferences.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
            )
        }.getOrNull()
    }

    fun save(apiKey: String, model: String) {
        require(apiKey.isNotBlank()) { "Entre une clé Anthropic." }
        preferences.edit().putString(KEY_API_KEY, encrypt(apiKey.trim()))
            .putString(KEY_MODEL, model.trim().ifBlank { DEFAULT_MODEL }).apply()
    }

    fun clear() = preferences.edit().clear().apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-4-5"
        private const val PREFS = "sommelier_configuration"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEYSTORE_ALIAS = "cellier_sommelier_key"
    }
}

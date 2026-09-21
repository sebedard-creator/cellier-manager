package com.cellier.manager.enrichment

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CompanionConfiguration(
    val baseUrl: String,
    val serverName: String,
    val certificateDerBase64Url: String,
    val certificateSha256: String,
    val deviceId: String,
    val token: String
)

class CompanionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun installationId(): String = preferences.getString(KEY_INSTALLATION, null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString(KEY_INSTALLATION, it).apply() }

    fun load(): CompanionConfiguration? {
        val baseUrl = preferences.getString(KEY_URL, null) ?: return null
        val certificate = preferences.getString(KEY_CERTIFICATE, null) ?: return null
        val fingerprint = preferences.getString(KEY_FINGERPRINT, null) ?: return null
        val encryptedToken = preferences.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            CompanionConfiguration(
                baseUrl = baseUrl,
                serverName = preferences.getString(KEY_NAME, "Mon ordinateur").orEmpty(),
                certificateDerBase64Url = certificate,
                certificateSha256 = fingerprint,
                deviceId = installationId(),
                token = decrypt(encryptedToken)
            )
        }.getOrNull()
    }

    fun save(baseUrl: String, serverName: String, certificate: String, fingerprint: String, token: String) {
        preferences.edit()
            .putString(KEY_URL, baseUrl.trimEnd('/'))
            .putString(KEY_NAME, serverName)
            .putString(KEY_CERTIFICATE, certificate)
            .putString(KEY_FINGERPRINT, fingerprint.lowercase())
            .putString(KEY_TOKEN, encrypt(token))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_URL).remove(KEY_NAME).remove(KEY_CERTIFICATE)
            .remove(KEY_FINGERPRINT).remove(KEY_TOKEN).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        require(all.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8)
    }

    companion object {
        private const val PREFS = "companion_configuration"
        private const val KEY_INSTALLATION = "installation_id"
        private const val KEY_URL = "base_url"
        private const val KEY_NAME = "server_name"
        private const val KEY_CERTIFICATE = "certificate_der"
        private const val KEY_FINGERPRINT = "certificate_sha256"
        private const val KEY_TOKEN = "token"
        private const val KEYSTORE_ALIAS = "cellier_companion_token"
    }
}

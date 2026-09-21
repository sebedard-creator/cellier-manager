package com.cellier.manager.enrichment

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.net.URI
import java.net.InetAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class PairingBundle(
    val baseUrl: String,
    val serverName: String,
    val certificateDerBase64Url: String,
    val certificateSha256: String,
    val pairingId: String,
    val pairingSecret: String
) {
    companion object {
        fun fromJson(value: String): PairingBundle {
            val json = JSONObject(value)
            require(json.getString("format") == "cellier-pairing") { "Fichier d’appairage inconnu." }
            require(json.getInt("version") == 1) { "Version d’appairage non supportée." }
            require(json.getString("role") == "ANDROID") { "Ce code n’est pas destiné à Android." }
            return PairingBundle(
                baseUrl = json.getString("baseUrl"),
                serverName = json.getString("serverName"),
                certificateDerBase64Url = json.getString("certificateDerBase64Url"),
                certificateSha256 = json.getString("certificateSha256"),
                pairingId = json.getString("pairingId"),
                pairingSecret = json.getString("pairingSecret")
            ).also { it.validate() }
        }
    }

    private fun validate() {
        val uri = runCatching { URI(baseUrl) }.getOrElse { throw IllegalArgumentException("Adresse du compagnon invalide.") }
        require(uri.scheme == "https" && uri.userInfo == null && uri.host != null) { "Le compagnon doit utiliser HTTPS." }
        val address = runCatching { InetAddress.getByName(uri.host) }.getOrElse {
            throw IllegalArgumentException("Adresse du compagnon introuvable.")
        }
        require(address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress) {
            "Le compagnon doit être sur le réseau local."
        }
        val certificate = CompanionClient.decodeCertificate(certificateDerBase64Url)
        val hash = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        require(hash.equals(certificateSha256, ignoreCase = true)) { "Empreinte du certificat invalide." }
    }
}

class CompanionClient private constructor(
    private val baseUrl: String,
    private val token: String?,
    private val client: OkHttpClient
) {
    suspend fun pair(store: CompanionStore, bundle: PairingBundle): CompanionConfiguration = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("pairingId", bundle.pairingId)
            .put("pairingSecret", bundle.pairingSecret)
            .put("deviceId", store.installationId())
            .put("name", android.os.Build.MODEL.take(100))
            .toString()
        val response = execute("/api/v1/pair", "POST", body, authenticated = false)
        val newToken = response.getString("token")
        store.save(bundle.baseUrl, bundle.serverName, bundle.certificateDerBase64Url, bundle.certificateSha256, newToken)
        CompanionConfiguration(bundle.baseUrl, bundle.serverName, bundle.certificateDerBase64Url,
            bundle.certificateSha256, store.installationId(), newToken)
    }

    suspend fun post(path: String, body: String, idempotencyKey: String? = null): JSONObject =
        withContext(Dispatchers.IO) { execute(path, "POST", body, idempotencyKey = idempotencyKey) }

    suspend fun get(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = requestBuilder(path, authenticated = true).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 204) return@withContext null
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw CompanionException(response.code, errorMessage(text))
            JSONObject(text)
        }
    }

    private fun execute(path: String, method: String, body: String, authenticated: Boolean = true, idempotencyKey: String? = null): JSONObject {
        val builder = requestBuilder(path, authenticated)
        idempotencyKey?.let { builder.header("Idempotency-Key", it) }
        val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = when (method) { "POST" -> builder.post(requestBody).build(); else -> error("Méthode non supportée") }
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw CompanionException(response.code, errorMessage(text))
            return JSONObject(text)
        }
    }

    private fun requestBuilder(path: String, authenticated: Boolean): Request.Builder =
        Request.Builder().url(baseUrl + path).apply {
            if (authenticated) header("Authorization", "Bearer ${token ?: throw IllegalStateException("Compagnon non appairé")}")
        }

    companion object {
        fun forPairing(bundle: PairingBundle): CompanionClient = CompanionClient(
            bundle.baseUrl, null, secureClient(decodeCertificate(bundle.certificateDerBase64Url))
        )
        fun from(configuration: CompanionConfiguration): CompanionClient = CompanionClient(
            configuration.baseUrl, configuration.token,
            secureClient(decodeCertificate(configuration.certificateDerBase64Url))
        )

        internal fun decodeCertificate(encoded: String): X509Certificate {
            val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
            val bytes = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        }

        private fun secureClient(certificate: X509Certificate): OkHttpClient {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry("companion", certificate)
            }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val trustManager = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
            val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
        }

        private fun errorMessage(text: String): String = runCatching {
            JSONObject(text).optJSONObject("error")?.optString("message")
                ?: JSONObject(text).optString("detail")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Erreur du compagnon."
    }
}

class CompanionException(val statusCode: Int, message: String) : Exception(message)

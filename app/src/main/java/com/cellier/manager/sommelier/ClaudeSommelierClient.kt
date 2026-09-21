package com.cellier.manager.sommelier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class SommelierRole(val apiName: String) {
    USER("user"),
    ASSISTANT("assistant")
}

data class SommelierChatMessage(
    val role: SommelierRole,
    val text: String
)

data class SommelierClaudeResponse(
    val text: String,
    val inputTokens: Int?,
    val outputTokens: Int?
)

class SommelierException(message: String) : Exception(message)

class ClaudeSommelierClient(
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = defaultClient()
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    suspend fun chat(
        systemPrompt: String,
        messages: List<SommelierChatMessage>
    ): SommelierClaudeResponse = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            throw SommelierException(
                "Clé Anthropic manquante. Configure Bromelier dans les réglages."
            )
        }

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 900)
            .put("system", systemPrompt)
            .put(
                "messages",
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", message.role.apiName)
                                .put("content", message.text)
                        )
                    }
                }
            )
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SommelierException(parseError(response.code, responseBody))
            }

            val json = JSONObject(responseBody)
            val text = json.optJSONArray("content")
                ?.let { content ->
                    buildString {
                        for (i in 0 until content.length()) {
                            val block = content.optJSONObject(i) ?: continue
                            if (block.optString("type") == "text") {
                                if (isNotEmpty()) append("\n\n")
                                append(block.optString("text").trim())
                            }
                        }
                    }.trim()
                }
                .orEmpty()

            if (text.isBlank()) {
                throw SommelierException("Claude a retourne une reponse vide.")
            }

            val usage = json.optJSONObject("usage")
            SommelierClaudeResponse(
                text = text,
                inputTokens = usage?.optInt("input_tokens")?.takeIf { it > 0 },
                outputTokens = usage?.optInt("output_tokens")?.takeIf { it > 0 }
            )
        }
    }

    private fun parseError(code: Int, body: String): String {
        val apiMessage = runCatching {
            JSONObject(body)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

        return when (code) {
            401 -> "Cle Anthropic invalide ou absente."
            403 -> "Acces Claude refuse pour cette cle Anthropic."
            429 -> "Limite Anthropic atteinte. Reessaie un peu plus tard."
            else -> apiMessage ?: "Erreur Claude HTTP $code."
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

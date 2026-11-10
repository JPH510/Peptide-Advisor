package com.peptideadvisor.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct-to-API. Single user, single device, key in the Keystore — no backend,
 * and nothing about the log ever leaves the phone except the question text and
 * the retrieved abstracts.
 */
class ClaudeClient(
    private val secrets: SecretStore,
    private val model: String = DEFAULT_MODEL,
    private val maxTokens: Int = 1500,
) {

    class MissingKeyException : Exception("No API key saved. Add one in Settings to ask questions.")
    class ApiException(val code: Int, body: String) :
        Exception("The API returned $code. ${body.take(300)}")

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun complete(system: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val key = secrets.get(SecretStore.API_KEY)?.takeIf { it.isNotBlank() }
                ?: throw MissingKeyException()

            val payload = JSONObject().apply {
                put("model", model)
                put("max_tokens", maxTokens)
                put("system", system)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", userMessage)
                ))
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("x-api-key", key)
                .addHeader("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, body)

                val blocks = JSONObject(body).optJSONArray("content")
                    ?: throw Exception("The API response couldn't be read.")

                val text = buildString {
                    for (i in 0 until blocks.length()) {
                        val block = blocks.optJSONObject(i) ?: continue
                        if (block.optString("type") == "text") {
                            if (isNotEmpty()) append("\n")
                            append(block.optString("text"))
                        }
                    }
                }

                text.ifBlank { throw Exception("The API returned an empty response.") }
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val DEFAULT_MODEL = "claude-sonnet-5"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}

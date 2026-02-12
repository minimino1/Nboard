package com.nboard.ime.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    suspend fun generateText(
        prompt: String,
        systemInstruction: String? = null,
        outputCharLimit: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Gemini API key missing"))
        }

        var lastNotFoundError: Exception? = null
        MODEL_FALLBACKS.forEach { model ->
            val result = generateWithModel(prompt, model, systemInstruction, outputCharLimit)
            if (result.isSuccess) {
                return@withContext result
            }

            val failure = result.exceptionOrNull()
            if (failure is GeminiHttpException && failure.httpCode == 404) {
                lastNotFoundError = failure
            } else {
                return@withContext result
            }
        }

        Result.failure(lastNotFoundError ?: IOException("No compatible Gemini model available"))
    }

    private fun generateWithModel(
        prompt: String,
        model: String,
        systemInstruction: String?,
        outputCharLimit: Int
    ): Result<String> {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val requestJson = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.3)
                    .put("maxOutputTokens", 256)
            )

        if (!systemInstruction.isNullOrBlank()) {
            requestJson.put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction.trim()))
                )
            )
        }

        val requestBody = requestJson
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorMessage = extractApiErrorMessage(bodyString)
                    return Result.failure(
                        GeminiHttpException(
                            httpCode = response.code,
                            detail = errorMessage ?: "Gemini request failed (${response.code})"
                        )
                    )
                }
                if (bodyString.isBlank()) {
                    return Result.failure(IOException("Gemini returned an empty response"))
                }

                val json = JSONObject(bodyString)
                var text = extractCandidateText(json)
                if (outputCharLimit > 0 && text.length > outputCharLimit) {
                    text = text.take(outputCharLimit).trimEnd().plus("…")
                }
                if (text.isNotBlank()) {
                    return Result.success(text)
                }

                val blockReason = json
                    .optJSONObject("promptFeedback")
                    ?.optString("blockReason")
                    .orEmpty()
                if (blockReason.isNotBlank()) {
                    return Result.failure(IOException("Gemini blocked the prompt ($blockReason)"))
                }

                Result.failure(IOException("Gemini response had no text output"))
            }
        } catch (error: Exception) {
            Result.failure(IOException(error.message ?: "Gemini request error", error))
        }
    }

    private fun extractCandidateText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        val output = StringBuilder()
        for (i in 0 until candidates.length()) {
            val parts = candidates
                .optJSONObject(i)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: continue

            for (j in 0 until parts.length()) {
                val text = parts.optJSONObject(j)?.optString("text").orEmpty().trim()
                if (text.isNotBlank()) {
                    if (output.isNotEmpty()) {
                        output.append('\n')
                    }
                    output.append(text)
                }
            }

            if (output.isNotEmpty()) {
                break
            }
        }
        return output.toString()
    }

    private fun extractApiErrorMessage(body: String): String? {
        if (body.isBlank()) {
            return null
        }

        return runCatching {
            JSONObject(body)
                .optJSONObject("error")
                ?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private data class GeminiHttpException(
        val httpCode: Int,
        val detail: String
    ) : IOException(detail)

    companion object {
        private val MODEL_FALLBACKS = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest"
        )
    }
}

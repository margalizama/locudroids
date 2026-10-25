package com.locudroids.app.ai

import com.locudroids.app.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Cliente mínimo de la API REST de Gemini (generateContent). */
class GeminiClient {

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        prompt: String,
        json: Boolean = false,
        maxTokens: Int = 1024,
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 1.0)
                    put("maxOutputTokens", maxTokens)
                    if (json) put("responseMimeType", "application/json")
                }
            )
        }

        val response = Http.postJsonRetry(
            url = url,
            body = body.toString(),
            headers = mapOf("x-goog-api-key" to apiKey),
            timeoutMs = 30_000,
        )

        val candidates = JSONObject(response).optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("Gemini no devolvió texto (respuesta vacía o bloqueada)")
        }
        val parts = candidates.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("Gemini no devolvió texto")

        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (!part.optBoolean("thought", false)) {
                    append(part.optString("text", ""))
                }
            }
        }.trim()

        if (text.isEmpty()) throw IllegalStateException("Gemini devolvió texto vacío")
        text
    }
}

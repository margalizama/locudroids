package com.locudroids.app.ai

import android.util.Base64
import com.locudroids.app.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Voz humana con Gemini TTS (endpoint generateContent). Una o dos voces por petición. */
class GeminiTts {

    data class TtsLine(val who: String, val voice: String, val style: String?, val text: String)

    suspend fun synthesize(apiKey: String, model: String, lines: List<TtsLine>): Pcm = withContext(Dispatchers.IO) {
        require(lines.isNotEmpty()) { "No hay texto para la voz" }
        val speakers = lines.map { it.who }.distinct()
        when (speakers.size) {
            1 -> single(apiKey, model, lines)
            2 -> multi(apiKey, model, lines, speakers)
            else -> synthesizeSequential(apiKey, model, lines)
        }
    }

    private suspend fun single(apiKey: String, model: String, lines: List<TtsLine>): Pcm {
        val voice = lines.first().voice
        val text = lines.joinToString(" ") { it.text }
        val style = lines.firstNotNullOfOrNull { it.style }
        return request(
            apiKey, model,
            primary = singleBody(voice, text, style),
            fallback = singleBody(voice, text, null),
        )
    }

    private suspend fun multi(apiKey: String, model: String, lines: List<TtsLine>, speakers: List<String>): Pcm {
        val voices = speakers.associateWith { who -> lines.first { it.who == who }.voice }
        return request(
            apiKey, model,
            primary = multiBody(lines, speakers, voices, withMeta = true),
            fallback = multiBody(lines, speakers, voices, withMeta = false),
        )
    }

    /** Más de 2 locutores en un corte: una petición por línea, unidas con una pausa corta. */
    suspend fun synthesizeSequential(apiKey: String, model: String, lines: List<TtsLine>): Pcm {
        val out = ByteArrayOutputStream()
        var rate = 24_000
        for (line in lines) {
            val pcm = single(apiKey, model, listOf(line))
            rate = pcm.sampleRate
            out.write(pcm.bytes)
            out.write(ByteArray(rate / 4 * 2)) // 250 ms de silencio
        }
        return Pcm(out.toByteArray(), rate)
    }

    private fun singleBody(voice: String, text: String, style: String?): JSONObject {
        val part = JSONObject().put("text", text)
        if (style != null) part.put("speech_metadata", JSONObject().put("style", style))
        return body(
            parts = JSONArray().put(part),
            speechConfig = JSONObject().put("voiceConfig", voiceConfig(voice)),
        )
    }

    private fun multiBody(
        lines: List<TtsLine>,
        speakers: List<String>,
        voices: Map<String, String>,
        withMeta: Boolean,
    ): JSONObject {
        val parts = JSONArray()
        if (withMeta) {
            for (line in lines) {
                val meta = JSONObject().put("speaker", line.who)
                line.style?.let { meta.put("style", it) }
                parts.put(JSONObject().put("text", line.text).put("speech_metadata", meta))
            }
        } else {
            parts.put(JSONObject().put("text", lines.joinToString("\n") { "${it.who}: ${it.text}" }))
        }
        val configs = JSONArray()
        for (who in speakers) {
            configs.put(
                JSONObject()
                    .put("speaker", who)
                    .put("voiceConfig", voiceConfig(voices.getValue(who)))
            )
        }
        return body(
            parts = parts,
            speechConfig = JSONObject().put(
                "multiSpeakerVoiceConfig",
                JSONObject().put("speakerVoiceConfigs", configs)
            ),
        )
    }

    private fun voiceConfig(voice: String): JSONObject =
        JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))

    private fun body(parts: JSONArray, speechConfig: JSONObject): JSONObject = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        .put(
            "generationConfig",
            JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", speechConfig)
        )

    /** Si el servidor rechaza el estilo (400), reintenta una vez con texto plano. */
    private suspend fun request(apiKey: String, model: String, primary: JSONObject, fallback: JSONObject): Pcm {
        return try {
            call(apiKey, model, primary)
        } catch (e: IOException) {
            val rejected = e.message?.startsWith("HTTP 400") == true
            if (rejected && fallback.toString() != primary.toString()) call(apiKey, model, fallback) else throw e
        }
    }

    private suspend fun call(apiKey: String, model: String, body: JSONObject): Pcm {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val response = Http.postJsonRetry(
            url = url,
            body = body.toString(),
            headers = mapOf("x-goog-api-key" to apiKey),
            timeoutMs = 60_000,
        )
        return parse(response)
    }

    private fun parse(response: String): Pcm {
        val parts = JSONObject(response)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: throw IllegalStateException("La respuesta de voz no trae audio")
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data", "")
            if (data.isEmpty()) continue
            val mime = inline.optString("mimeType", inline.optString("mime_type", ""))
            return Wav.toPcm(Base64.decode(data, Base64.DEFAULT), mime)
        }
        throw IllegalStateException("La respuesta de voz no trae audio")
    }
}

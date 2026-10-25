package com.locudroids.app.data

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Cliente HTTP mínimo (sin dependencias externas). Bloqueante: llamar desde Dispatchers.IO. */
object Http {

    fun get(url: String, timeoutMs: Int = 10_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            return readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 20_000,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    /** Igual que [postJson] pero reintenta una vez si el servidor responde 429 (límite) o 503 (saturado). */
    suspend fun postJsonRetry(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 30_000,
    ): String {
        var attempt = 0
        while (true) {
            try {
                return postJson(url, body, headers, timeoutMs)
            } catch (e: IOException) {
                val msg = e.message.orEmpty()
                val retriable = msg.startsWith("HTTP 429") || msg.startsWith("HTTP 503")
                if (!retriable || attempt >= 1) throw e
                attempt++
                delay(2_500)
            }
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IOException("HTTP $code: ${text.take(160)}")
        }
        return text
    }
}

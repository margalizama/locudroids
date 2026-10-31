package com.locudroids.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Lee los titulares de un RSS de noticias (sin clave). Usa Google Noticias en español de México. */
object NewsParser {
    private val item = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val cdata = Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)
    private val tags = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")

    /** Titulares limpios (sin el nombre del medio al final), sin repetidos. */
    fun parse(xml: String, limit: Int): List<String> {
        val out = LinkedHashSet<String>()
        for (m in item.findAll(xml)) {
            val raw = title.find(m.groupValues[1])?.groupValues?.get(1) ?: continue
            var t = cdata.find(raw)?.groupValues?.get(1) ?: raw
            t = decode(t).replace(tags, " ").replace(spaces, " ").trim()
            // "Titular - Medio": se quita el medio.
            val dash = t.lastIndexOf(" - ")
            if (dash > 20) t = t.substring(0, dash).trim()
            if (t.length in 15..220) out += t
            if (out.size >= limit) break
        }
        return out.toList()
    }

    private fun decode(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&amp;", "&")
}

class NewsClient {
    private var cache: Triple<String, Long, List<String>>? = null

    /** Hasta [limit] titulares recientes; de la [city] si se da, si no, los principales de México. Vacío si falla. */
    suspend fun headlines(city: String, limit: Int = 6): List<String> {
        val key = city.trim().lowercase()
        val now = System.currentTimeMillis()
        cache?.let { (k, t, list) -> if (k == key && now - t < CACHE_MS) return list }
        return withContext(Dispatchers.IO) {
            try {
                val url = if (key.isEmpty()) {
                    "https://news.google.com/rss?hl=es-419&gl=MX&ceid=MX:es-419"
                } else {
                    "https://news.google.com/rss/search?q=${URLEncoder.encode("${city.trim()} when:1d", "UTF-8")}" +
                        "&hl=es-419&gl=MX&ceid=MX:es-419"
                }
                val list = NewsParser.parse(Http.get(url, 8_000), limit)
                cache = Triple(key, System.currentTimeMillis(), list)
                list
            } catch (e: Exception) {
                Log.w(TAG, "No se pudieron obtener noticias: ${e.message}")
                emptyList()
            }
        }
    }

    private companion object {
        const val TAG = "NewsClient"
        const val CACHE_MS = 20 * 60_000L
    }
}

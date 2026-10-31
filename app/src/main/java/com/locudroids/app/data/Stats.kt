package com.locudroids.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class SongStat(val plays: Int, val lastPlayed: Long)

/** Cuántas veces se ha escuchado cada canción (por ruta de archivo). */
class StatsRepository(private val file: File, private val io: CoroutineScope? = null) {

    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Map<String, SongStat>> = _stats.asStateFlow()

    fun record(path: String, now: Long = System.currentTimeMillis()) {
        _stats.update { m ->
            val old = m[path]
            m + (path to SongStat((old?.plays ?: 0) + 1, now))
        }
        val snapshot = _stats.value
        if (io != null) io.launch { persist(snapshot) } else persist(snapshot)
    }

    private fun persist(map: Map<String, SongStat>) {
        synchronized(this) {
            val o = JSONObject()
            map.forEach { (p, s) -> o.put(p, JSONObject().put("n", s.plays).put("t", s.lastPlayed)) }
            file.writeAtomic(JSONObject().put("stats", o).toString())
        }
    }

    private fun load(): Map<String, SongStat> {
        val o = file.readJson()?.optJSONObject("stats") ?: return emptyMap()
        val out = HashMap<String, SongStat>()
        for (key in o.keys()) {
            val s = o.optJSONObject(key) ?: continue
            out[key] = SongStat(s.optInt("n"), s.optLong("t"))
        }
        return out
    }

    companion object {
        /** Las más escuchadas primero; a igualdad, la más reciente. */
        fun top(stats: Map<String, SongStat>, limit: Int): List<String> =
            stats.entries
                .sortedWith(compareByDescending<Map.Entry<String, SongStat>> { it.value.plays }.thenByDescending { it.value.lastPlayed })
                .take(limit)
                .map { it.key }

        fun recent(stats: Map<String, SongStat>, limit: Int): List<String> =
            stats.entries.sortedByDescending { it.value.lastPlayed }.take(limit).map { it.key }
    }
}

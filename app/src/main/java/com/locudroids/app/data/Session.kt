package com.locudroids.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lo último que se estaba escuchando. */
data class SavedSession(
    val paths: List<String>,
    val currentPath: String?,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
)

/** Recuerda la cola y en qué canción y segundo se quedó. La cola y la posición van en archivos separados (la posición se guarda seguido). */
class SessionStore(
    private val queueFile: File,
    private val positionFile: File,
    private val io: CoroutineScope? = null,
) {
    fun saveQueue(paths: List<String>) {
        run { synchronized(queueFile) { queueFile.writeAtomic(JSONObject().put("paths", JSONArray(paths)).toString()) } }
    }

    fun savePosition(currentPath: String?, index: Int, positionMs: Long, shuffle: Boolean) {
        run {
            synchronized(positionFile) {
                positionFile.writeAtomic(
                    JSONObject()
                        .put("path", currentPath ?: "")
                        .put("index", index)
                        .put("pos", positionMs)
                        .put("shuffle", shuffle)
                        .toString()
                )
            }
        }
    }

    fun load(): SavedSession? {
        val arr = queueFile.readJson()?.optJSONArray("paths") ?: return null
        val paths = (0 until arr.length()).map { arr.getString(it) }
        if (paths.isEmpty()) return null
        val p = positionFile.readJson()
        return SavedSession(
            paths = paths,
            currentPath = p?.optString("path")?.takeIf { it.isNotBlank() },
            index = p?.optInt("index", 0) ?: 0,
            positionMs = p?.optLong("pos", 0L) ?: 0L,
            shuffle = p?.optBoolean("shuffle", false) ?: false,
        )
    }

    private fun run(block: () -> Unit) {
        if (io != null) io.launch { block() } else block()
    }

    companion object {
        /**
         * Reconstruye la cola con las canciones que todavía existen. Devuelve la cola, el índice donde seguir
         * y el segundo (0 si la canción ya no está). null si no queda nada.
         */
        fun resolve(saved: SavedSession, byPath: Map<String, Song>): Resolved? {
            val songs = saved.paths.mapNotNull { byPath[it] }
            if (songs.isEmpty()) return null
            val found = saved.currentPath?.let { cp -> songs.indexOfFirst { it.path == cp } } ?: -1
            return if (found >= 0) {
                Resolved(songs, found, saved.positionMs.coerceAtLeast(0L))
            } else {
                Resolved(songs, saved.index.coerceIn(0, songs.lastIndex), 0L)
            }
        }
    }

    data class Resolved(val songs: List<Song>, val index: Int, val positionMs: Long)
}

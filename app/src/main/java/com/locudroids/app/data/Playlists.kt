package com.locudroids.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Una lista de reproducción. Guarda las rutas de los archivos (sobreviven a que cambien los IDs de MediaStore). */
data class Playlist(val id: String, val name: String, val paths: List<String>)

class PlaylistRepository(private val file: File, private val io: CoroutineScope? = null) {

    private val _lists = MutableStateFlow(load())
    val lists: StateFlow<List<Playlist>> = _lists.asStateFlow()

    fun create(name: String, paths: List<String> = emptyList()): Playlist {
        val list = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "Mi lista" }, paths.distinct())
        change { it + list }
        return list
    }

    fun rename(id: String, name: String) = change { all ->
        all.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
    }

    fun delete(id: String) = change { all -> all.filterNot { it.id == id } }

    /** Agrega canciones al final sin repetir las que ya están. */
    fun add(id: String, paths: List<String>) = change { all ->
        all.map { if (it.id == id) it.copy(paths = (it.paths + paths).distinct()) else it }
    }

    fun removePath(id: String, path: String) = change { all ->
        all.map { if (it.id == id) it.copy(paths = it.paths - path) else it }
    }

    fun get(id: String): Playlist? = _lists.value.firstOrNull { it.id == id }

    private fun change(transform: (List<Playlist>) -> List<Playlist>) {
        _lists.update(transform)
        val snapshot = _lists.value
        if (io != null) io.launch { persist(snapshot) } else persist(snapshot)
    }

    private fun persist(lists: List<Playlist>) {
        synchronized(this) { file.writeAtomic(toJson(lists).toString()) }
    }

    private fun load(): List<Playlist> {
        val arr = file.readJson()?.optJSONArray("lists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val paths = o.optJSONArray("paths")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            Playlist(id, o.optString("name", "Lista"), paths)
        }
    }

    companion object {
        fun toJson(lists: List<Playlist>): JSONObject = JSONObject().put(
            "lists",
            JSONArray().also { arr ->
                lists.forEach {
                    arr.put(
                        JSONObject()
                            .put("id", it.id)
                            .put("name", it.name)
                            .put("paths", JSONArray(it.paths))
                    )
                }
            },
        )
    }
}

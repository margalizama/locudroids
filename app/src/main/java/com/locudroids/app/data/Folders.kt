package com.locudroids.app.data

data class FolderEntry(val path: String, val name: String, val count: Int)

data class Crumb(val name: String, val path: String)

data class BrowserState(
    val path: String = "",
    val atRoot: Boolean = true,
    val breadcrumb: List<Crumb> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    /** Canciones que están directamente en esta carpeta. */
    val songs: List<Song> = emptyList(),
    /** Total de canciones en esta carpeta y todas sus subcarpetas. */
    val total: Int = 0,
    val name: String = "",
)

/** Convierte la lista plana de canciones en un árbol de carpetas navegable. */
object Folders {

    private const val INTERNAL_ROOT = "/storage/emulated/0"

    fun build(songs: List<Song>, requested: String?): BrowserState {
        if (songs.isEmpty()) return BrowserState()

        val common = commonDir(songs.map { it.folder }.distinct())
        val root = if (common == INTERNAL_ROOT || common.startsWith("$INTERNAL_ROOT/")) INTERNAL_ROOT else common
        val path = requested?.takeIf { it == root || it.startsWith("$root/") } ?: root
        val prefix = "$path/"

        val direct = songs.filter { it.folder == path }.sortedBy { it.path.lowercase() }

        val counts = linkedMapOf<String, Int>()
        for (s in songs) {
            if (s.folder.startsWith(prefix)) {
                val child = s.folder.removePrefix(prefix).substringBefore('/')
                counts[child] = (counts[child] ?: 0) + 1
            }
        }
        val folders = counts
            .map { (name, count) -> FolderEntry(prefix + name, name, count) }
            .sortedBy { it.name.lowercase() }

        return BrowserState(
            path = path,
            atRoot = path == root,
            breadcrumb = breadcrumb(root, path),
            folders = folders,
            songs = direct,
            total = direct.size + folders.sumOf { it.count },
            name = displayName(root, path),
        )
    }

    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    private fun displayName(root: String, path: String): String =
        if (path == root) rootName(root) else path.substringAfterLast('/')

    private fun rootName(root: String): String =
        if (root == INTERNAL_ROOT) "Almacenamiento" else root.substringAfterLast('/').ifEmpty { "Música" }

    private fun breadcrumb(root: String, path: String): List<Crumb> {
        val crumbs = mutableListOf(Crumb(rootName(root), root))
        if (path != root) {
            var acc = root
            for (segment in path.removePrefix("$root/").split('/')) {
                acc = "$acc/$segment"
                crumbs += Crumb(segment, acc)
            }
        }
        return crumbs
    }

    private fun commonDir(dirs: List<String>): String {
        if (dirs.isEmpty()) return ""
        var common = dirs.first().split('/')
        for (d in dirs.drop(1)) {
            val parts = d.split('/')
            var i = 0
            while (i < common.size && i < parts.size && common[i] == parts[i]) i++
            common = common.take(i)
        }
        return common.joinToString("/")
    }
}

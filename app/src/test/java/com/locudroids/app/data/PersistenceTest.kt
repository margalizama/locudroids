package com.locudroids.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PersistenceTest {

    private fun tmp(): File = Files.createTempDirectory("locudroids").toFile()

    private fun song(path: String) = Song(path.hashCode().toLong(), path, null, null, null, 120_000L, path, path.substringBeforeLast('/'))

    @Test
    fun playlistsSurviveReload() {
        val file = File(tmp(), "playlists.json")
        val repo = PlaylistRepository(file)
        val list = repo.create("Gym", listOf("/m/a.mp3", "/m/b.mp3", "/m/a.mp3"))
        repo.add(list.id, listOf("/m/b.mp3", "/m/c.mp3"))
        repo.removePath(list.id, "/m/a.mp3")
        repo.rename(list.id, "Gym 2")

        val again = PlaylistRepository(file).lists.value
        assertEquals(1, again.size)
        assertEquals("Gym 2", again[0].name)
        assertEquals(listOf("/m/b.mp3", "/m/c.mp3"), again[0].paths)

        repo.delete(list.id)
        assertEquals(0, PlaylistRepository(file).lists.value.size)
    }

    @Test
    fun blankNameGetsDefault() {
        val repo = PlaylistRepository(File(tmp(), "p.json"))
        assertEquals("Mi lista", repo.create("   ").name)
    }

    @Test
    fun corruptFileIsIgnored() {
        val file = File(tmp(), "p.json").apply { writeText("{no es json") }
        assertEquals(0, PlaylistRepository(file).lists.value.size)
    }

    @Test
    fun statsCountAndRank() {
        val file = File(tmp(), "stats.json")
        val repo = StatsRepository(file)
        repo.record("/a", now = 1)
        repo.record("/b", now = 2)
        repo.record("/b", now = 3)
        repo.record("/c", now = 4)

        val reloaded = StatsRepository(file).stats.value
        assertEquals(2, reloaded["/b"]?.plays)
        assertEquals(listOf("/b", "/c", "/a"), StatsRepository.top(reloaded, 10))
        assertEquals(listOf("/c", "/b"), StatsRepository.recent(reloaded, 2))
    }

    @Test
    fun sessionRestoresCurrentSongAndPosition() {
        val dir = tmp()
        val store = SessionStore(File(dir, "q.json"), File(dir, "pos.json"))
        assertNull(store.load())
        store.saveQueue(listOf("/a", "/b", "/c"))
        store.savePosition("/b", 1, 42_000L, shuffle = true)

        val saved = store.load()
        assertNotNull(saved)
        val byPath = listOf("/a", "/b", "/c").associateWith { song(it) }
        val r = SessionStore.resolve(saved!!, byPath)!!
        assertEquals(1, r.index)
        assertEquals(42_000L, r.positionMs)
        assertEquals(true, saved.shuffle)
    }

    @Test
    fun sessionSkipsMissingFilesAndResetsPosition() {
        val saved = SavedSession(listOf("/a", "/gone", "/c"), currentPath = "/gone", index = 1, positionMs = 9_000L, shuffle = false)
        val byPath = listOf("/a", "/c").associateWith { song(it) }
        val r = SessionStore.resolve(saved, byPath)!!
        assertEquals(listOf("/a", "/c"), r.songs.map { it.path })
        assertEquals(1, r.index)
        assertEquals(0L, r.positionMs)
        assertNull(SessionStore.resolve(saved, emptyMap()))
    }
}

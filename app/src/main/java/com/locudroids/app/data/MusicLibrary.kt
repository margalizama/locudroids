package com.locudroids.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val uri: Uri,
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}

/** Escanea los MP3 del teléfono usando MediaStore. */
object MusicLibrary {

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val collection: Uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.MIME_TYPE} = ? AND " +
            "${MediaStore.Audio.Media.DURATION} >= 30000"
        val args = arrayOf("audio/mpeg")
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()
        context.contentResolver.query(collection, projection, selection, args, order)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                songs += Song(
                    id = id,
                    title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Sin título",
                    artist = c.getString(artistCol).cleanTag(),
                    album = c.getString(albumCol).cleanTag(),
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(collection, id),
                )
            }
        }
        songs
    }

    /** MediaStore usa "<unknown>" cuando no hay etiqueta. */
    private fun String?.cleanTag(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
}

package com.locudroids.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carga portadas incrustadas en los MP3, con caché en memoria. */
object ArtLoader {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val missing = HashSet<String>()

    suspend fun load(context: Context, songId: Long, sizePx: Int): Bitmap? {
        val key = "$songId@$sizePx"
        cache.get(key)?.let { return it }
        if (synchronized(missing) { key in missing }) return null

        val bitmap = withContext(Dispatchers.IO) { decode(context, songId, sizePx) }
        if (bitmap != null) {
            cache.put(key, bitmap)
        } else {
            synchronized(missing) { missing.add(key) }
        }
        return bitmap
    }

    private fun decode(context: Context, songId: Long, sizePx: Int): Bitmap? {
        val uri = MusicLibrary.uriFor(songId)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val bytes = retriever.embeddedPicture ?: return null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= sizePx) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                } finally {
                    retriever.release()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

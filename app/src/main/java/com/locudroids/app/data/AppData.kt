package com.locudroids.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Datos propios de la app (listas, estadísticas, sesión). El servicio y la pantalla comparten la misma instancia (mismo proceso). */
class AppData private constructor(context: Context) {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = context.filesDir

    val playlists = PlaylistRepository(File(dir, "playlists.json"), io)
    val stats = StatsRepository(File(dir, "stats.json"), io)
    val session = SessionStore(File(dir, "queue.json"), File(dir, "position.json"), io)
    val analysis = AnalysisRepository(File(dir, "analysis.json"), io)
    val analyzer = AnalysisRunner(context.applicationContext, analysis, io)

    companion object {
        @Volatile
        private var instance: AppData? = null

        fun get(context: Context): AppData =
            instance ?: synchronized(this) {
                instance ?: AppData(context.applicationContext).also { instance = it }
            }
    }
}

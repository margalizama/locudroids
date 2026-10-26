package com.locudroids.app.player

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.locudroids.app.ai.LocalSpeech
import com.locudroids.app.ai.Pcm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Una línea para la voz local: [sid] es el hablante del modelo Kokoro y [speed] la velocidad. */
class LocalLine(val text: String, val sid: Int, val speed: Float)

/**
 * Voz neuronal que corre dentro del teléfono (Kokoro, con el motor sherpa-onnx). No usa internet ni cuotas.
 * El modelo viaja en los assets de la app; espeak-ng (que convierte texto en sonidos) necesita archivos reales,
 * así que sus datos se copian una sola vez a la carpeta interna.
 */
class LocalTts(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var engine: OfflineTts? = null
    private var sampleRate = 24_000
    private var langIndex = 0

    /** true si esta versión de la app trae el modelo (el APK de GitHub Actions sí lo trae). */
    fun isBundled(): Boolean = try {
        appContext.assets.list(ASSET_DIR)?.contains(MODEL_FILE) == true
    } catch (e: IOException) {
        false
    }

    /** Carga el modelo en segundo plano para que el primer corte no espere. */
    suspend fun warmUp() {
        try {
            mutex.withLock { withContext(Dispatchers.Default) { ensureEngine() } }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar la voz local: ${e.message}")
        }
    }

    /** Genera el audio de todas las líneas, una a la una (el motor no admite llamadas en paralelo). */
    suspend fun synthesize(lines: List<LocalLine>): Pcm = mutex.withLock {
        withContext(Dispatchers.Default) {
            var chunks = generate(lines)
            while (chunks.isEmpty() && fallbackLang()) chunks = generate(lines)
            if (chunks.isEmpty()) throw IllegalStateException("la voz local no produjo audio")
            LocalSpeech.toPcm(chunks, sampleRate)
        }
    }

    private fun generate(lines: List<LocalLine>): List<FloatArray> {
        val tts = ensureEngine()
        val chunks = ArrayList<FloatArray>()
        for (line in lines) {
            val text = line.text.trim()
            if (text.isEmpty()) continue
            val audio = tts.generate(text = text, sid = line.sid, speed = line.speed)
            if (audio.samples.isNotEmpty()) {
                sampleRate = audio.sampleRate
                chunks += audio.samples
            }
        }
        return chunks
    }

    /** Si el español latino no produce audio, se reintenta una vez con el español de espeak "es". */
    private fun fallbackLang(): Boolean {
        if (langIndex >= LANGS.lastIndex) return false
        langIndex++
        release()
        return true
    }

    fun release() {
        engine?.release()
        engine = null
    }

    private fun ensureEngine(): OfflineTts {
        engine?.let { return it }
        check(isBundled()) { "esta versión de la app no trae la voz local" }
        val dataDir = prepareEspeakData()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$ASSET_DIR/$MODEL_FILE",
                    voices = "$ASSET_DIR/voices.bin",
                    tokens = "$ASSET_DIR/tokens.txt",
                    dataDir = dataDir.absolutePath,
                    lexicon = "",
                    lang = LANGS[langIndex],
                ),
                numThreads = THREADS,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
            silenceScale = 0.15f,
        )
        val created = OfflineTts(assetManager = appContext.assets, config = config)
        engine = created
        sampleRate = created.sampleRate()
        Log.i(TAG, "Voz local lista (${created.numSpeakers()} hablantes, $sampleRate Hz)")
        return created
    }

    /** Copia espeak-ng-data de los assets a la carpeta interna (solo la primera vez por versión). */
    private fun prepareEspeakData(): File {
        val root = File(appContext.filesDir, "voz-local")
        val target = File(root, "espeak-ng-data")
        val marker = File(root, "listo-$DATA_VERSION")
        if (marker.exists() && target.isDirectory) return target
        root.deleteRecursively()
        copyAssetDir("$ASSET_DIR/espeak-ng-data", target)
        marker.writeText("ok")
        return target
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        } else {
            dest.mkdirs()
            for (name in children) copyAssetDir("$assetPath/$name", File(dest, name))
        }
    }

    private companion object {
        const val TAG = "LocalTts"
        const val ASSET_DIR = "kokoro"
        const val MODEL_FILE = "model.int8.onnx"
        /** Español latinoamericano de espeak-ng (seseo, como en México); "es" es el respaldo. */
        val LANGS = listOf("es-419", "es")
        const val THREADS = 4
        const val DATA_VERSION = 1
    }
}

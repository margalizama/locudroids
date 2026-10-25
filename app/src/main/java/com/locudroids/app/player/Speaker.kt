package com.locudroids.app.player

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID

/** Envoltura del TextToSpeech de Android (la voz del sistema, la misma que usa Home Assistant). */
class Speaker(context: Context) {

    private val ready = CompletableDeferred<Boolean>()
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                var result = engine.setLanguage(Locale("es", "MX"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = engine.setLanguage(Locale("es"))
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "El motor de voz no tiene español instalado; se usa su idioma por defecto")
                }
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ready.complete(true)
            } else {
                Log.w(TAG, "No se pudo iniciar el TTS (status=$status)")
                ready.complete(false)
            }
        }
    }

    /** Habla el texto y espera a que termine. Devuelve true si se dijo completo. */
    suspend fun speak(text: String, rate: Float, pitch: Float = 1f): Boolean {
        if (!ready.await()) return false
        val engine = tts ?: return false
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)

        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.complete(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.complete(false)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == id) done.complete(false)
            }
        })

        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            return false
        }
        return try {
            withTimeout(MAX_SPEECH_MS) { done.await() }
        } catch (e: TimeoutCancellationException) {
            engine.stop()
            false
        } catch (e: CancellationException) {
            engine.stop()
            throw e
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val TAG = "Speaker"
        private const val MAX_SPEECH_MS = 60_000L
    }
}

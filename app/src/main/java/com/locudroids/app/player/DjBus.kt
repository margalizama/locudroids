package com.locudroids.app.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado del locutor compartido entre el servicio de reproducción y la pantalla (mismo proceso). */
object DjBus {
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _lastLine = MutableStateFlow<String?>(null)
    val lastLine: StateFlow<String?> = _lastLine.asStateFlow()

    /** Quién está al aire ("Rafa y Vale"). */
    private val _onAir = MutableStateFlow<String?>(null)
    val onAir: StateFlow<String?> = _onAir.asStateFlow()

    /** Ánimo de la canción que suena, según el guion ("fiesta", "romántica"...). */
    private val _mood = MutableStateFlow<String?>(null)
    val mood: StateFlow<String?> = _mood.asStateFlow()

    /** Qué voz se usó en el último corte ("Voz: Gemini", o por qué se usó otra / se omitió). */
    private val _voiceInfo = MutableStateFlow<String?>(null)
    val voiceInfo: StateFlow<String?> = _voiceInfo.asStateFlow()

    private val _skip = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skip: SharedFlow<Unit> = _skip.asSharedFlow()

    fun setSpeaking(value: Boolean) {
        _speaking.value = value
    }

    fun setLine(value: String?) {
        _lastLine.value = value
    }

    fun setOnAir(value: String?) {
        _onAir.value = value
    }

    fun setMood(value: String?) {
        _mood.value = value
    }

    fun setVoiceInfo(value: String?) {
        _voiceInfo.value = value
    }

    /** Pide al servicio que calle a los locutores (la música sigue). */
    fun requestSkip() {
        _skip.tryEmit(Unit)
    }
}

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

    private val _skip = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val skip: SharedFlow<Unit> = _skip.asSharedFlow()

    fun setSpeaking(value: Boolean) {
        _speaking.value = value
    }

    fun setLine(value: String?) {
        _lastLine.value = value
    }

    /** Pide al servicio que calle al locutor (la música sigue). */
    fun requestSkip() {
        _skip.tryEmit(Unit)
    }
}

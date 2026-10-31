package com.locudroids.app.player

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.locudroids.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Al conectarse un dispositivo Bluetooth elegido en Ajustes, empieza a tocar la música. */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = device?.address ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                val s = SettingsRepository(app).current()
                if (!s.btAutoPlay || s.btDevices.none { it.substringBefore('|') == address }) return@launch
                Log.i(TAG, "Conectado $address: esperando a que el audio salga por Bluetooth")
                if (AutoPlay.waitForBluetoothAudio(app, ROUTE_TIMEOUT_MS)) {
                    delay(800)
                    AutoPlay.resume(app)
                } else {
                    Log.w(TAG, "El audio no se enrutó a Bluetooth a tiempo; no se empieza a tocar")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-play falló: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BluetoothReceiver"
        const val ROUTE_TIMEOUT_MS = 8_000L
    }
}

/** Reanuda la música desde fuera de la pantalla (por ejemplo, al conectar Bluetooth). */
object AutoPlay {
    const val CMD_RESUME = "locudroids.RESUME"
    private const val TAG = "AutoPlay"

    private var held: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Espera (sin bloquear) a que el sistema tenga una salida A2DP; así no suena por la bocina del teléfono. */
    suspend fun waitForBluetoothAudio(context: Context, timeoutMs: Long): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }) return true
            delay(400)
        }
        return false
    }

    /**
     * Conecta con el servicio de reproducción y le pide reanudar. El controlador se queda retenido mientras suena:
     * mantiene vivo el servicio aunque Android no deje convertirlo en servicio en primer plano desde el fondo.
     */
    fun resume(context: Context) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                val controller = try {
                    future.get()
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo conectar con el reproductor: ${e.message}")
                    return@addListener
                }
                held?.release()
                held = controller
                controller.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (!playWhenReady) releaseHeld(controller)
                    }
                })
                controller.sendCustomCommand(SessionCommand(CMD_RESUME, Bundle.EMPTY), Bundle.EMPTY)
                handler.postDelayed({ if (!controller.isPlaying) releaseHeld(controller) }, 120_000L)
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun releaseHeld(controller: MediaController) {
        if (held === controller) held = null
        controller.release()
    }
}

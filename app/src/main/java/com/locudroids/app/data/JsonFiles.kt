package com.locudroids.app.data

import org.json.JSONObject
import java.io.File

/** Lee un JSON guardado; si no existe o está dañado devuelve null (nunca lanza). */
internal fun File.readJson(): JSONObject? = try {
    if (exists()) JSONObject(readText()) else null
} catch (e: Exception) {
    null
}

/** Escribe sin dejar el archivo a medias si la app se cierra en medio de la escritura. */
internal fun File.writeAtomic(text: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        writeText(text)
        tmp.delete()
    }
}

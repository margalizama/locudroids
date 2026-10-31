package com.locudroids.app.data

import com.locudroids.app.ai.LocalVoices

/** Un locutor del programa: su nombre, su voz de Gemini, su voz local (hablante de Kokoro) y su personalidad. */
data class Announcer(
    val name: String,
    val voice: String,
    val persona: String,
    val localVoice: Int = LocalVoices.ALEX,
) {
    companion object {
        fun defaults(): List<Announcer> = listOf(
            Announcer(
                name = "Rafa",
                voice = "Fenrir",
                persona = "El conductor del programa. Enérgico y carismático, con voz de locutor de éxitos. " +
                    "Lleva el ritmo, presenta las canciones con actitud, grita las frases de la estación, hace los anuncios de parodia " +
                    "y los horóscopos locos, y siempre tiene un comentario de fiesta.",
                localVoice = LocalVoices.ALEX,
            ),
            Announcer(
                name = "Vale",
                voice = "Aoede",
                persona = "La pícara. Coqueta, rápida y sarcástica. Lanza albures y dobles sentidos con una sonrisa, " +
                    "manda saludos atrevidos y le encanta molestar a Rafa.",
                localVoice = LocalVoices.DORA,
            ),
        )
    }
}

/** Voces predefinidas de Gemini TTS con una descripción corta. */
object GeminiVoices {
    val all: List<Pair<String, String>> = listOf(
        "Zephyr" to "Brillante",
        "Puck" to "Alegre",
        "Charon" to "Informativa",
        "Kore" to "Firme",
        "Fenrir" to "Entusiasta",
        "Leda" to "Juvenil",
        "Orus" to "Firme",
        "Aoede" to "Fresca",
        "Callirrhoe" to "Relajada",
        "Autonoe" to "Brillante",
        "Enceladus" to "Susurrante",
        "Iapetus" to "Clara",
        "Umbriel" to "Tranquila",
        "Algieba" to "Suave",
        "Despina" to "Suave",
        "Erinome" to "Clara",
        "Algenib" to "Rasposa",
        "Rasalgethi" to "Informativa",
        "Laomedeia" to "Alegre",
        "Achernar" to "Suave",
        "Alnilam" to "Firme",
        "Schedar" to "Pareja",
        "Gacrux" to "Madura",
        "Pulcherrima" to "Directa",
        "Achird" to "Amigable",
        "Zubenelgenubi" to "Casual",
        "Vindemiatrix" to "Gentil",
        "Sadachbia" to "Animada",
        "Sadaltager" to "Sabia",
        "Sulafat" to "Cálida",
    )

    fun label(name: String): String {
        val desc = all.firstOrNull { it.first == name }?.second
        return if (desc != null) "$name · $desc" else name
    }
}

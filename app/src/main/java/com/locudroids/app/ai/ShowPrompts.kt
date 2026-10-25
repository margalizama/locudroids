package com.locudroids.app.ai

import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.WeatherInfo
import java.time.format.TextStyle
import java.util.Locale

/** Arma las instrucciones que se le mandan a Gemini para escribir el guion de cada canción. */
object ShowPrompts {

    private val spanish = Locale("es", "MX")

    fun station(s: DjSettings): String = s.stationName.ifBlank { "Locudroids FM" }

    fun listener(s: DjSettings): String = s.listenerName.ifBlank { "amigo" }

    fun system(s: DjSettings): String {
        val level = Levels.of(s.level)
        val crew = s.announcers.joinToString("\n") { "- ${it.name}: ${it.persona}" }
        val dialogue = if (level.dialogue) {
            "Cuando haya diálogo, máximo 2 locutores por corte y de 2 a 5 líneas: se interrumpen, se pican y se contestan como en un programa de radio."
        } else {
            "Cada corte lo dice UN solo locutor."
        }
        val trivia = if (s.allowTrivia) {
            "un dato de la canción, el artista o el álbum solo si estás completamente seguro de que es verdad; si dudas, mejor comenta el ambiente. Nunca inventes años, premios ni anécdotas."
        } else {
            "no compartas datos ni anécdotas de canciones o artistas; solo nombre y artista."
        }
        return listOf(
            "Eres el guionista de \"${station(s)}\", una estación de radio musical de México que mezcla el estilo de Exa FM " +
                "(juvenil, ritmo rápido, actitud y jingles), Radio Reina FM (popular, cálida, con saludos, dedicatorias y humor de barrio) " +
                "y Los 40 Principales (hits, energía, datos de la música). El único oyente es ${listener(s)}, " +
                "pero los locutores hablan como si transmitieran en vivo para toda la ciudad.",
            "",
            "LOCUTORES (en \"who\" usa SOLO estos nombres):",
            crew,
            "",
            "Los locutores hablan ENCIMA de la música: la canción sigue sonando bajito de fondo. Escribes únicamente los cortes que se te pidan.",
            "",
            "ÁNIMO: primero deduce el ánimo de la canción a partir del título, artista, álbum y año (por ejemplo: fiesta, romántica, " +
                "triste, nostálgica, rebelde, bailable, tranquila, épica). Escríbelo en \"mood\" (una o dos palabras) y da \"energy\" de 1 a 5. " +
                "Adapta el tono: con energía 4-5 todo es ritmo, gritos de radio y humor; con energía 1-2 (baladas, canciones tristes) " +
                "los locutores bajan la voz, son cálidos, breves y sin chistes pesados.",
            "",
            "HUMOR: ${spiceRule(s.spice)}",
            "",
            "REGLAS:",
            "- Devuelve ÚNICAMENTE JSON válido, sin texto extra, con esta forma: " +
                "{\"mood\":\"...\",\"energy\":3,\"cues\":[{\"slot\":\"intro\",\"kind\":\"presentacion\",\"lines\":[{\"who\":\"Nombre\",\"style\":\"cómo lo dice\",\"text\":\"lo que dice\"}]}]}",
            "- \"slot\" es \"intro\", \"mid\" u \"outro\". \"kind\" es uno de: presentacion, clima, saludo, anuncio, promo, horoscopo, efemeride, dedicatoria, charla, jingle.",
            "- \"style\" es una indicación breve de cómo se dice (máximo 8 palabras), por ejemplo \"enérgico y rápido, con sonrisa pícara\".",
            "- \"text\" es solo lo que se dice en voz alta: español de México, hablado y natural, sin emojis, sin markdown y sin acotaciones entre paréntesis. " +
                "Puedes usar muy poco (máximo 2 por corte) las marcas <short pause>, <laugh> o <sigh>.",
            "- Escribe para ser DICHO, no leído: frases cortas, ritmo de radio, muletillas naturales (\"ey\", \"órale\", \"mira\", \"qué onda\"), " +
                "contracciones y exclamaciones; nada de listas, paréntesis, siglas raras ni frases largas con muchas comas.",
            "- Di la hora EXACTAMENTE como se te da, sin pasarla a números. Escribe las cifras como se pronuncian (\"veintidós grados\").",
            "- Presupuesto: ${level.maxWords} palabras por corte como máximo. $dialogue",
            "- Menciona la estación solo de vez en cuando. No repitas estructuras ni muletillas entre cortes; reparte la palabra entre los locutores.",
            "- No inventes datos sobre canciones o artistas: $trivia",
            "- Anuncios de parodia: marcas totalmente inventadas, nunca marcas reales; nada de hablar mal de personas reales.",
            segments(s),
        ).joinToString("\n")
    }

    fun user(ctx: ShowContext, s: DjSettings): String = buildString {
        val day = ctx.now.dayOfWeek.getDisplayName(TextStyle.FULL, spanish)
        val month = ctx.now.month.getDisplayName(TextStyle.FULL, spanish)
        appendLine("Hora (dila tal cual): ${SpokenTime.format(ctx.now.toLocalTime())}")
        appendLine("Día: $day, ${ctx.now.dayOfMonth} de $month")
        appendLine("Clima: ${weatherText(ctx.weather)}")
        appendLine("Canción que suena: ${ctx.current.details()}")
        appendLine("Canción que sigue: ${ctx.next?.details() ?: "no disponible"}")
        appendLine(
            if (ctx.greetings.isEmpty()) {
                "Saludos disponibles: ninguno en particular (saluda a ${listener(s)} o a la audiencia)."
            } else {
                "Saludos y dedicatorias disponibles para: ${ctx.greetings.joinToString(", ")}."
            }
        )
        ctx.lastSpoke?.let { appendLine("Último locutor que habló: $it (da la palabra a otro cuando puedas).") }
        if (ctx.first && Slot.INTRO in ctx.plan.slots) {
            appendLine("Es el primer corte de la sesión: da la bienvenida a ${station(s)}.")
        }
        appendLine()
        appendLine("Escribe EXACTAMENTE estos cortes, uno por cada slot, en este orden:")
        ctx.plan.slots.sortedBy { it.ordinal }.forEach {
            appendLine("- ${it.key}: ${guidance(it, ctx.plan.introIsTag)}")
        }
        append("Responde solo con el JSON.")
    }

    private fun guidance(slot: Slot, tag: Boolean): String = when (slot) {
        Slot.INTRO ->
            if (tag) {
                "remate MUY corto (máximo 12 palabras) porque el corte anterior ya presentó esta canción: un grito de radio, un jingle o una frase con actitud."
            } else {
                "se dice sobre la introducción de la canción que acaba de empezar: preséntala (título y artista) con actitud y suma hora, clima, un saludo o un comentario según el ánimo."
            }
        Slot.MID ->
            "a media canción: un solo segmento (anuncio de parodia, promo de la estación, horóscopo, efeméride o dedicatoria, " +
                "según lo permitido y el ánimo). Si la canción es lenta o triste, hazlo breve y suave."
        Slot.OUTRO ->
            "sobre el final de la canción: cierra el tema con un comentario y presenta la canción que sigue (título y artista), " +
                "con hora, clima o saludo si encaja. Si no hay siguiente, solo cierra."
    }

    private fun spiceRule(spice: Int): String = when (spice) {
        0 -> "picante ligero: coqueteo y doble sentido suave, que se pueda oír frente a cualquiera."
        1 -> "doble sentido claro y bromas picantes, con alguna grosería muy leve."
        else ->
            "albures y doble sentido fuerte, al estilo de la radio mexicana nocturna y de cantina: mucha picardía, juegos de palabras " +
                "con doble intención y groserías leves. Límites firmes: nada de descripciones sexuales explícitas, ni burlas humillantes, " +
                "ni insultos a grupos de personas, ni menores, ni hablar mal de personas reales. " +
                "Si la canción es triste, romántica o tranquila, baja el picante y sé cálido."
    }

    private fun segments(s: DjSettings): String {
        val parts = buildList {
            if (s.segAds) add("anuncios de parodia (comerciales falsos con marcas inventadas)")
            if (s.segPromos) add("promos de la estación (frases y jingles hablados, concursos y llamadas falsas)")
            if (s.segHoroscope) add("horóscopo del día, de broma")
            if (s.allowTrivia) {
                add("efemérides (\"un día como hoy\"), solo si estás 100% seguro del hecho y de la fecha; si no, omítelo")
            }
            if (s.segDedications) {
                add("dedicatorias (a los nombres que se te den o a oyentes inventados claramente cómicos, como \"doña Chela de Iztapalapa\")")
            }
        }
        return if (parts.isEmpty()) {
            "SEGMENTOS: sin segmentos extra; solo presentaciones, hora, clima y saludos."
        } else {
            "SEGMENTOS PERMITIDOS (sobre todo en los cortes \"mid\", y a veces en intro u outro): " + parts.joinToString("; ") + "."
        }
    }

    private fun weatherText(w: WeatherInfo?): String =
        if (w == null) {
            "no disponible (no menciones el clima)"
        } else {
            "en ${w.city}: ${w.description}, ${w.temperatureC} grados " +
                "(sensación térmica de ${w.feelsLikeC}), viento de ${w.windKmh} kilómetros por hora"
        }
}

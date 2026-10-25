package com.locudroids.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locudroids.app.ai.Levels
import com.locudroids.app.data.Announcer
import com.locudroids.app.data.DjSettings
import com.locudroids.app.data.GeminiVoices
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val testStatus by vm.testStatus.collectAsStateWithLifecycle()

    val initial = settings
    if (initial == null) {
        Text("Cargando…", modifier = modifier.padding(24.dp), color = Tech.TextDim)
    } else {
        SettingsForm(
            initial = initial,
            testStatus = testStatus,
            onChange = vm::saveSettings,
            onTest = vm::testDj,
            modifier = modifier,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TechLabel(title, color = Tech.Cyan)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Tech.TextMain)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Tech.TextDim)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = Tech.TextMain)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Tech.TextDim)
        }
    }
}

@Composable
private fun VoicePicker(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Voz: ${GeminiVoices.label(current)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GeminiVoices.all.forEach { (name, description) ->
                DropdownMenuItem(
                    text = { Text("$name · $description") },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsForm(
    initial: DjSettings,
    testStatus: String?,
    onChange: (DjSettings) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(initial) }
    fun edit(transform: (DjSettings) -> DjSettings) {
        draft = transform(draft)
        onChange(draft)
    }
    fun editAnnouncer(index: Int, transform: (Announcer) -> Announcer) {
        edit { s -> s.copy(announcers = s.announcers.mapIndexed { i, a -> if (i == index) transform(a) else a }) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "AJUSTES",
            modifier = Modifier.padding(start = 4.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = 5.sp,
            color = Tech.TextMain,
        )

        SectionCard("PROGRAMA") {
            SwitchRow("Locutores activados", null, draft.djEnabled) { v -> edit { it.copy(djEnabled = v) } }

            Text("¿Qué tanto hablan?", color = Tech.TextMain)
            Levels.all.forEach { info ->
                ChoiceRow(
                    selected = draft.level == info.level,
                    title = "${info.level} · ${info.name}",
                    detail = info.detail,
                ) { edit { it.copy(level = info.level) } }
            }
            Text(
                "Los locutores deducen el ánimo de cada canción y se adaptan: en una balada bajan la voz y no hay mitad de canción.",
                style = MaterialTheme.typography.bodySmall,
                color = Tech.TextDim,
            )
        }

        SectionCard("HUMOR") {
            ChoiceRow(draft.spice == 0, "Picante ligero", "Coqueteo y doble sentido suave") { edit { it.copy(spice = 0) } }
            ChoiceRow(draft.spice == 1, "Doble sentido", "Bromas picantes, groserías muy leves") { edit { it.copy(spice = 1) } }
            ChoiceRow(draft.spice == 2, "Albures fuertes", "Estilo radio nocturna y de cantina (sin contenido explícito)") {
                edit { it.copy(spice = 2) }
            }
        }

        SectionCard("SEGMENTOS") {
            SwitchRow("Anuncios de parodia", "Comerciales falsos con marcas inventadas", draft.segAds) { v ->
                edit { it.copy(segAds = v) }
            }
            SwitchRow("Promos de la estación", "Jingles hablados, concursos y llamadas falsas", draft.segPromos) { v ->
                edit { it.copy(segPromos = v) }
            }
            SwitchRow("Horóscopo", "El horóscopo del día, de broma", draft.segHoroscope) { v ->
                edit { it.copy(segHoroscope = v) }
            }
            SwitchRow("Dedicatorias", "A tus nombres o a oyentes inventados", draft.segDedications) { v ->
                edit { it.copy(segDedications = v) }
            }
            SwitchRow("Datos y efemérides", "Solo si el modelo está seguro de que son ciertos", draft.allowTrivia) { v ->
                edit { it.copy(allowTrivia = v) }
            }
        }

        SectionCard("LOCUTORES") {
            draft.announcers.forEachIndexed { index, announcer ->
                if (index > 0) HorizontalDivider(color = Tech.GlassBorder)
                OutlinedTextField(
                    value = announcer.name,
                    onValueChange = { v -> editAnnouncer(index) { it.copy(name = v) } },
                    label = { Text("Locutor ${index + 1}: nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                VoicePicker(announcer.voice) { voice -> editAnnouncer(index) { it.copy(voice = voice) } }
                OutlinedTextField(
                    value = announcer.persona,
                    onValueChange = { v -> editAnnouncer(index) { it.copy(persona = v) } },
                    label = { Text("Personalidad") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard("ESTACIÓN") {
            OutlinedTextField(
                value = draft.stationName,
                onValueChange = { v -> edit { it.copy(stationName = v) } },
                label = { Text("Nombre de la estación") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.listenerName,
                onValueChange = { v -> edit { it.copy(listenerName = v) } },
                label = { Text("Tu nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.greetingsTo,
                onValueChange = { v -> edit { it.copy(greetingsTo = v) } },
                label = { Text("Saludos y dedicatorias para") },
                supportingText = { Text("Nombres separados por comas. Ej.: mamá, los de la oficina, Luis") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.city,
                onValueChange = { v -> edit { it.copy(city = v) } },
                label = { Text("Ciudad para el clima") },
                supportingText = { Text("Ejemplo: Ciudad de México") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("GEMINI") {
            OutlinedTextField(
                value = draft.geminiApiKey,
                onValueChange = { v -> edit { it.copy(geminiApiKey = v) } },
                label = { Text("Clave de API") },
                supportingText = { Text("Se guarda solo en este teléfono. Sin clave, los locutores usan frases fijas.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.geminiModel,
                onValueChange = { v -> edit { it.copy(geminiModel = v) } },
                label = { Text("Modelo del guion") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                "Voces de Gemini (más humanas)",
                "Si se apaga, o si falla, se usa la voz de Android",
                draft.geminiVoice,
            ) { v -> edit { it.copy(geminiVoice = v) } }
            SwitchRow(
                "Si Gemini falla, usar la voz de Android",
                "Suena robótica. Apagado: el corte se omite y la música sigue sola",
                draft.androidFallback,
            ) { v -> edit { it.copy(androidFallback = v) } }
            OutlinedTextField(
                value = draft.ttsModel,
                onValueChange = { v -> edit { it.copy(ttsModel = v) } },
                label = { Text("Modelo de voz") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("VOZ DE ANDROID (RESPALDO)") {
            Text("Velocidad: ${"%.1f".format(draft.speechRate)}x", color = Tech.TextMain)
            Slider(
                value = draft.speechRate,
                onValueChange = { v -> edit { it.copy(speechRate = (v * 10).roundToInt() / 10f) } },
                valueRange = 0.7f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = Tech.Cyan,
                    activeTrackColor = Tech.Cyan,
                    inactiveTrackColor = Color(0x33FFFFFF),
                ),
            )
        }

        Button(
            onClick = onTest,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Tech.Cyan,
                contentColor = Color(0xFF04101A),
            ),
        ) {
            Text("Probar a los locutores")
        }
        if (testStatus != null) {
            Text(testStatus, style = MaterialTheme.typography.bodyMedium, color = Tech.TextMain)
        }
        Spacer(Modifier.height(16.dp))
    }
}

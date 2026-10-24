package com.locudroids.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import com.locudroids.app.data.DjSettings
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

        SectionCard("LOCUTOR") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Locutor activado", Modifier.weight(1f), color = Tech.TextMain)
                Switch(checked = draft.djEnabled, onCheckedChange = { v -> edit { it.copy(djEnabled = v) } })
            }

            Text("¿Qué tanto habla?", color = Tech.TextMain)
            IntensityOption(
                selected = draft.intensity == 1,
                title = "Suave",
                detail = "Un corte cada 2 canciones",
            ) { edit { it.copy(intensity = 1) } }
            IntensityOption(
                selected = draft.intensity == 2,
                title = "Radio",
                detail = "Un corte al inicio de cada canción",
            ) { edit { it.copy(intensity = 2) } }
            IntensityOption(
                selected = draft.intensity == 3,
                title = "Intenso",
                detail = "Al inicio y al final de cada canción",
            ) { edit { it.copy(intensity = 3) } }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Datos de canciones y artistas", color = Tech.TextMain)
                    Text(
                        "El locutor comenta datos solo si está seguro de ellos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tech.TextDim,
                    )
                }
                Switch(checked = draft.allowTrivia, onCheckedChange = { v -> edit { it.copy(allowTrivia = v) } })
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
                value = draft.djName,
                onValueChange = { v -> edit { it.copy(djName = v) } },
                label = { Text("Nombre del locutor") },
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
                label = { Text("Mandar saludos a") },
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
                supportingText = { Text("Se guarda solo en este teléfono. Sin clave, el locutor usa frases fijas.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.geminiModel,
                onValueChange = { v -> edit { it.copy(geminiModel = v) } },
                label = { Text("Modelo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("VOZ") {
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
            Button(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Tech.Cyan,
                    contentColor = Color(0xFF04101A),
                ),
            ) {
                Text("Probar al locutor")
            }
            if (testStatus != null) {
                Text(testStatus, style = MaterialTheme.typography.bodyMedium, color = Tech.TextMain)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IntensityOption(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
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

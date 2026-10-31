package com.locudroids.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locudroids.app.ai.MoodMixer
import com.locudroids.app.ai.Moods

@Composable
fun MoodTab(vm: MainViewModel, modifier: Modifier = Modifier) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val analysis by vm.analysisState.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    var order by rememberSaveable { mutableStateOf(MixOrder.SIMILAR) }

    val existing = remember(songs) { songs.mapTo(HashSet()) { it.path } }
    val counts = remember(profiles, existing) { MoodMixer.moodCounts(profiles, existing) }
    val analyzed = remember(profiles, existing) { profiles.count { it.key in existing && it.value.bpm != null } }

    Column(modifier) {
        GlassCard(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TechLabel("ANÁLISIS DE LA BIBLIOTECA")
                if (analysis.running) {
                    Text(analysis.phase, color = Tech.TextMain)
                    LinearProgressIndicator(
                        progress = { if (analysis.total > 0) analysis.done.toFloat() / analysis.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Tech.Cyan,
                    )
                    TechLabel("${analysis.done} / ${analysis.total}")
                    OutlinedButton(onClick = { vm.stopAnalysis() }) { Text("Detener") }
                } else {
                    Text(
                        "$analyzed de ${songs.size} canciones analizadas (ritmo y energía).",
                        color = Tech.TextMain,
                    )
                    Button(
                        onClick = { vm.startAnalysis() },
                        colors = ButtonDefaults.buttonColors(containerColor = Tech.Cyan, contentColor = Color(0xFF04101A)),
                    ) { Text(if (analyzed == 0) "Analizar mi música" else "Analizar lo que falta") }
                }
                analysis.note?.let { Text(it, color = Tech.TextDim, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(order == MixOrder.SIMILAR, { order = MixOrder.SIMILAR }, label = { Text("Mismo ritmo") })
            FilterChip(order == MixOrder.UP, { order = MixOrder.UP }, label = { Text("Subiendo") })
            FilterChip(order == MixOrder.DOWN, { order = MixOrder.DOWN }, label = { Text("Bajando") })
        }

        OutlinedButton(
            onClick = { vm.djFromCurrent() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  DJ: seguir con canciones parecidas a la que suena")
        }

        if (counts.isEmpty()) {
            Text(
                "Cuando termine el análisis aparecerán aquí tus mezclas por estado de ánimo.",
                modifier = Modifier.padding(24.dp),
                color = Tech.TextDim,
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(counts, key = { it.first }) { (mood, n) ->
                ListItem(
                    headlineContent = { Text(Moods.label(mood), color = Tech.TextMain) },
                    supportingContent = { Text("$n canciones", color = Tech.TextDim) },
                    trailingContent = {
                        IconButton(onClick = { vm.playMood(mood, order) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir mezcla", tint = Tech.Cyan)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.playMood(mood, order) },
                )
                HorizontalDivider(color = Tech.GlassBorder)
            }
        }
    }
}

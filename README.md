# Locudroids

Reproductor de MP3 para Android con un **locutor IA privado** estilo radio musical (tipo Exa FM):
un DJ solo para ti que habla **encima de la música**, te da la hora y el clima, manda saludos y
presenta las canciones.

## Cómo funciona

- Reproduce los MP3 locales del teléfono (MediaStore) con Media3/ExoPlayer, también con la pantalla apagada.
- **Explorador de carpetas:** navega por carpetas con migas de pan, reproduce una carpeta completa
  (con sus subcarpetas) en orden o aleatorio, y busca en la lista de todas las canciones.
- **Locutor estilo radio:** la música no se detiene; baja de volumen mientras el locutor habla y vuelve a subir.
  - *Suave:* un corte cada 2 canciones.
  - *Radio* (por defecto): un corte al inicio de cada canción (nombre y artista, hora, clima, saludo y, si está seguro, un dato).
  - *Intenso:* además, un corte cerca del final que cierra la canción y presenta la siguiente.
- **Personalidad dinámica:** cada corte usa un tono distinto (enérgico, relajado, bromista, nostálgico,
  misterioso, elegante) según la hora, si llueve y el tono anterior.
- **Saludos:** configura nombres en Ajustes y el locutor va rotando a quién saluda.
- **Texto:** lo escribe Gemini (API REST). Sin clave, o si la llamada falla, usa frases de respaldo.
- **Voz:** el motor TTS de Android (la voz del sistema). **Clima:** Open-Meteo (gratis, sin clave).
- El botón **CALLAR** de la pantalla principal detiene al locutor al instante.
- Diseño oscuro tecnológico: tarjetas de vidrio, espectro animado, portada del MP3 e insignia "AL AIRE".

## Primeros pasos

1. Descarga el APK desde **Actions → Build APK → Artifacts**, o ábrelo con Android Studio.
2. Abre la app y da permiso de audio y notificaciones.
3. En **Ajustes** pon tu clave de Gemini, tu ciudad, tu nombre y los saludos. Usa **Probar al locutor**
   para comprobar clave, clima y voz sin esperar a una canción.
4. En **Biblioteca** elige una carpeta o una canción.

La clave de Gemini se guarda solo en el teléfono (DataStore, sin cifrar). Para uso personal está bien;
si algún día se publica la app, conviene cifrarla o moverla a un servidor propio.

## Estructura

```
app/src/main/java/com/locudroids/app/
├── MainActivity.kt
├── ai/        GeminiClient, DjScript (prompts, tonos, respaldo), DjWriter, SpokenTime (hora hablada)
├── data/      Settings (DataStore), WeatherClient, MusicLibrary, Folders (árbol de carpetas), ArtLoader, Http
├── player/    PlaybackService, DjController (locutor sobre la música), Speaker (TTS), DjBus
└── ui/        App (navegación), PlayerScreen, LibraryScreen, SettingsScreen, Components, Theme, MainViewModel
```

## Ideas para después

- Escoger la voz del TTS y probar voces en la nube.
- Noticias breves, agenda del día o recordatorios dichos por el locutor.
- Notas del usuario para el locutor ("hoy tengo junta a las 5").
- Listas de reproducción y cola visible.

# Locudroids

Reproductor de MP3 para Android con un **locutor IA privado**: un DJ solo para ti que te saluda,
te dice el clima, anuncia las canciones y habla entre ellas.

## Cómo funciona

- Reproduce los MP3 locales del teléfono (MediaStore) con Media3/ExoPlayer, también con la pantalla apagada.
- **Apertura:** al dar play por primera vez, el locutor saluda, comenta el clima y presenta la primera canción.
- **Entre canciones:** cada N canciones (por defecto 2) la música se pausa al terminar el tema, el locutor
  habla y la música reanuda sola. El texto se prepara mientras suena la canción anterior, así no hay silencios largos.
- **Personalidad dinámica:** en cada intervención se elige un tono distinto (enérgico, relajado, bromista,
  nostálgico, misterioso, elegante) según la hora del día, si llueve y el tono de la vez anterior.
- **Texto:** lo escribe Gemini (API REST). Sin clave, o si la llamada falla, usa frases de respaldo.
- **Voz:** el motor TTS de Android (la voz del sistema).
- **Clima:** Open-Meteo (gratis, sin clave).
- Tocar play mientras habla el locutor se salta su intervención.

## Primeros pasos

1. Sube el proyecto a GitHub (ya hecho) y descarga el APK desde **Actions → Build APK → Artifacts**,
   o ábrelo con Android Studio y ejecútalo.
2. Abre la app y da permiso de audio y notificaciones.
3. En **Ajustes** pon tu clave de API de Gemini, tu ciudad y tu nombre. Usa **Probar al locutor**
   para comprobar clave, clima y voz sin esperar a una canción.
4. Toca una canción.

La clave de Gemini se guarda solo en el teléfono (DataStore, sin cifrar). Para uso personal está bien;
si algún día se publica la app, conviene cifrarla o moverla a un servidor propio.

## Estructura

```
app/src/main/java/com/locudroids/app/
├── MainActivity.kt
├── ai/        GeminiClient, DjScript (prompts, tonos, respaldo), DjWriter
├── data/      Settings (DataStore), WeatherClient, MusicLibrary, Http
├── player/    PlaybackService, DjController (lógica del locutor), Speaker (TTS), DjForwardingPlayer, DjBus
└── ui/        MainViewModel, Screens, Theme
```

## Ideas para después

- Escoger la voz del TTS y probar voces en la nube.
- Noticias breves, agenda del día o recordatorios dichos por el locutor.
- Notas del usuario para el locutor ("hoy tengo junta a las 5").
- Portadas de álbum y listas de reproducción.

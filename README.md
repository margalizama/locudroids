# Locudroids

Reproductor de MP3 para Android con **locutores IA privados** estilo radio musical mexicana
(mezcla de Exa FM, Radio Reina FM y Los 40 Principales): un programa de radio solo para ti que habla
**encima de la música**, da la hora y el clima, manda saludos, hace anuncios y presenta las canciones.

## Reproductor (0.4.0)

- **Recuerda** la cola, la canción y el segundo donde te quedaste.
- **Listas de reproducción** (pestaña LISTAS y ⋮ en cada canción) y **TOP** (más escuchadas / recientes; una canción cuenta al oírla 30 s o la mitad).
- **DJ por ánimo** (pestaña ÁNIMO): Gemini clasifica el ánimo por título/artista y la app mide BPM y energía en el teléfono; mezclas por ánimo
  (mismo ritmo, subiendo o bajando) y "DJ: seguir con canciones parecidas".
- **Auto-mix** (Ajustes → Mezcla): fundido (crossfade) o mezcla con ritmo experimental (iguala el tempo y alinea los golpes).
- **Bluetooth**: empieza a tocar al conectar los dispositivos que elijas (pide permiso de Bluetooth y, para funcionar con la app cerrada, "sin restricciones" de batería).
- **Locutores**: 2 (Rafa y Vale), con voz de Gemini y respaldo local; hablan del tema de la canción, datos del artista, ritmo medido y noticias breves (titulares de Google Noticias).

## Cómo funciona

- Reproduce los MP3 locales del teléfono (MediaStore) con Media3/ExoPlayer, también con la pantalla apagada.
- **Explorador de carpetas:** navega por carpetas, reproduce una carpeta completa (en orden o aleatorio) y busca canciones.
- **Tres locutores** con nombre, voz y personalidad propios (editables en Ajustes). Por defecto:
  Rafa (el conductor enérgico), Vale (la pícara) y Chuy (el bromista). Se turnan y, en los niveles altos, platican entre ellos.
- **Cinco niveles de intensidad:**
  1. *Susurro*: un comentario corto cada 4 canciones.
  2. *Suave*: un corte al inicio de una de cada 2 canciones.
  3. *Radio* (por defecto): un corte al inicio de cada canción.
  4. *Intenso*: inicio de cada canción y presentación de la siguiente cada 2 canciones, con charla entre locutores.
  5. *Locura*: inicio, mitad y final de cada canción, con anuncios, promos y diálogos.
- **Ánimo de la canción:** Gemini deduce el ánimo por título, artista, álbum y año y adapta el tono.
  En una balada o canción triste los locutores bajan la voz y no hay corte a media canción.
- **Humor:** picante ligero, doble sentido o albures fuertes (sin contenido sexual explícito ni burlas humillantes).
- **Segmentos:** anuncios de parodia (marcas inventadas), promos de la estación, horóscopo, efemérides
  (solo si el modelo está seguro) y dedicatorias a los nombres que configures.
- **Voz humana:** voces de Gemini TTS (una por locutor). Si Gemini falla (p. ej. HTTP 429 por cuota),
  se pausa 10 min y los cortes salen con una **voz neuronal local** (Kokoro vía sherpa-onnx, dentro de la app,
  sin internet ni cuotas): Alex, Dora y Santa, una por locutor. La voz de Android es solo último recurso (apagado por defecto).
  Ajustes → VOCES permite elegir Automático / Solo local / Solo Gemini.
  El modelo (~140 MB) y las bibliotecas se bajan con `scripts/fetch-voice-assets.sh` (lo hace el CI; para compilar en local, córrelo antes).
- **Sin esperas:** el guion y el audio de la siguiente canción se preparan mientras suena la actual.
- La música baja de volumen mientras hablan los locutores. El botón **CALLAR** los detiene al instante.
- **Clima:** Open-Meteo (gratis, sin clave).

## Primeros pasos

1. Descarga el APK desde **Actions → Build APK → Artifacts**, o ábrelo con Android Studio.
2. Abre la app y da permiso de audio y notificaciones.
3. En **Ajustes** pon tu clave de Gemini, tu ciudad, tu nombre y los nombres para saludos.
4. Pulsa **Probar a los locutores**: muestra el guion, el ánimo y si la voz fue de Gemini o de Android
   (y el error exacto si Gemini falló).
5. En **Biblioteca** elige una carpeta o una canción.

La clave de Gemini se guarda solo en el teléfono (DataStore, sin cifrar). Para uso personal está bien;
si algún día se publica la app, conviene cifrarla o moverla a un servidor propio.

## Modelos

- Guion: `gemini-3.5-flash-lite` (editable en Ajustes).
- Voz: `gemini-3.8-flash-tts` (editable en Ajustes). La app usa el endpoint `generateContent`.

## Estructura

```
app/src/main/java/com/locudroids/app/
├── MainActivity.kt
├── ai/        ShowPrompts, ShowWriter, ShowParser, CuePlanner (niveles), GeminiClient, GeminiTts, Wav, SpokenTime
├── data/      Settings (DataStore), Announcer (locutores y voces), WeatherClient, MusicLibrary, Folders, ArtLoader, Http
├── player/    PlaybackService, DjController (el programa), VoiceDirector, PcmPlayer, Speaker (voz de Android), DjBus
└── ui/        App, PlayerScreen, LibraryScreen, SettingsScreen, Components, Theme, MainViewModel
```

Las pruebas unitarias (`app/src/test`) cubren el planificador de niveles, la hora hablada, el lector del
guion JSON y la lectura del audio. GitHub Actions las corre junto con la compilación.

## Ideas para después

- Noticias breves, agenda del día o recordatorios dichos por los locutores.
- Notas para los locutores ("hoy tengo junta a las 5").
- Listas de reproducción y cola visible.

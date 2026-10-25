# Locudroids

Reproductor de MP3 para Android con **locutores IA privados** estilo radio musical mexicana
(mezcla de Exa FM, Radio Reina FM y Los 40 Principales): un programa de radio solo para ti que habla
**encima de la música**, da la hora y el clima, manda saludos, hace anuncios y presenta las canciones.

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
- **Voz humana:** voces de Gemini TTS (una por locutor). Si la voz de Gemini falla o se apaga,
  se usa la voz de Android con un tono distinto por locutor.
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

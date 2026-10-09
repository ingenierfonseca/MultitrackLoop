# Multitrack Player — Android

Aplicación profesional para reproducir multitracks de audio (MP3/WAV) sincronizadas desde el inicio, con cambio de BPM en tiempo real sin pérdida de sincronización.

## Decisiones arquitectónicas clave

### 1. Por qué NO usar N ExoPlayers

Cada `ExoPlayer` corre con su propio reloj de audio y su propia latencia de `pre-roll`. Iniciar varios con `play()` provoca desfases iniciales de 20–100 ms y drift progresivo cuando se cambia la velocidad, porque cada uno aplica su propio `setPlaybackSpeed` sobre su propio `MediaClock`. No hay forma sencilla de sincronizarlos con precisión sub-frame.

### 2. Motor de audio elegido: PCM unificado + AudioTrack

- Un único `AudioTrack` en `MODE_STREAM` con `ENCODING_PCM_FLOAT`.
- Las pistas se decodifican **una vez** (en `prepare()`, en background) a un buffer float estéreo.
- En cada callback de audio, un único hilo productor (Mixer) lee de cada buffer, mezcla con `soft-clip tanh`, y escribe en el `AudioTrack`.
- Como solo hay UN `AudioTrack` y UN reloj de salida, las pistas son por construcción **coherentes en sample-frame**: el desfase entre pistas es literalmente imposible a nivel de hardware.

### 3. PlaybackClock

`PlaybackClock` es la única fuente de verdad del tiempo de la sesión. Se actualiza atómicamente en el hilo de audio (`AtomicLong`) y expone un `StateFlow` con snapshots a ~30 Hz para la UI. La UI nunca hace polling de `getCurrentPosition()` de un player.

### 4. Cambio de BPM sin drift

Cada `PcmSource` mantiene su PCM al sample rate *original* (no pre-resampleamos al bus). En cada `read(out, speedFactor)`, el `LinearResampler` aplica:

```
step = sourceSampleRate / busSampleRate * (originalBpm / currentBpm)
```

Como `originalBpm` y `currentBpm` son **los mismos para todas las pistas en el mismo instante**, el factor es idéntico → todas las pistas aceleran/frenan la misma cantidad → la sincronización se preserva.

### 5. Loop

La duración del mix se define como la pista más larga. Cuando una pista termina antes:
- Si loop está ON: se rebobina al siguiente callback (alineada con el bus).
- Si loop está OFF: el motor se detiene al final de la pista más larga.

### 6. Limitaciones

- En Android no hay garantía de tiempo real estricto; el jitter depende de la carga del sistema. Usamos un buffer de ~200 ms en `AudioTrack` para absorberlo.
- El resampler lineal introduce leve distorsión al cambiar BPM muy rápido (zipper noise). El diseño permite reemplazarlo por SOLA/SoundTouch sin tocar el motor.
- Cargar el WAV/MP3 entero en RAM funciona bien para stems típicos (<200 MB). Para stems masivos se sustituiría por una fuente memory-mapped detrás de la misma interfaz `PcmSource`.

## Estructura

```
app/src/main/java/com/multitrack/player
├── domain/                       # modelos puros + casos de uso + contratos
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/                         # implementaciones Android
│   ├── audio/                    # motor + mixer + clock
│   ├── audio/source/             # WAV / MP3 decoders
│   ├── datasource/
│   └── repository/
├── presentation/                 # Compose + ViewModels
│   ├── navigation/
│   ├── screens/{trackselection,playback}/
│   └── components/
└── di/                           # Hilt
```

## Build

```bash
./gradlew assembleDebug
```

## Tests

```bash
./gradlew test
```

## Hoja de ruta

- Volumen / mute / solo / paneo por pista.
- Metrónomo + count-in.
- MIDI / OSC.
- Foreground service para reproducción en background.
- EQ y efectos.
- Exportación del mix.

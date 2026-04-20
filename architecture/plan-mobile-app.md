# Plan: App MIDAS — Dashboard + Grabación de Llamadas en Vivo (KMP)

## Contexto

MIDAS tiene un API backend (FastAPI + Gemini + PostgreSQL) y una extensión Chrome para WhatsApp Web. Ahora necesitamos una **app multiplataforma** (iOS-first, Android, desktop) que sirva como:

1. **Dashboard**: ver conversaciones, intenciones detectadas, solicitudes de crédito generadas
2. **Grabación de llamadas en vivo**: durante una llamada telefónica, el asesor activa el micrófono de la app para capturar el audio, transcribirlo en tiempo real, y detectar intención financiera al finalizar

La extensión Chrome coexiste con la app como productos complementarios.

**Stack**: Kotlin Multiplatform (KMP) + Compose Multiplatform (estable para iOS desde mayo 2025).

**Fecha**: 2026-04-10

---

## Flujo de Grabación en Vivo

```
1. Asesor inicia/recibe llamada telefónica con cliente
2. Abre MIDAS → "Grabar llamada" → ingresa nombre del cliente (o selecciona de recientes)
3. Presiona "Iniciar grabación" → app activa micrófono del dispositivo
4. Asesor habla con la llamada en altavoz (o audífonos + mic libre)
5. Audio capturado → chunks enviados por WebSocket al backend
6. Backend → Google Cloud Speech-to-Text (streaming) → transcripción parcial
7. Transcripción aparece en tiempo real en la pantalla del asesor
8. Asesor cuelga → presiona "Detener"
9. Backend ejecuta Gemini intent detection sobre transcripción completa
10. Si hay intención → ofrece generar solicitud de crédito
```

---

## Arquitectura de la App

```
midas/apps/mobile/
├── shared/                          # KMP — lógica compartida
│   └── src/
│       ├── commonMain/
│       │   ├── data/
│       │   │   ├── api/
│       │   │   │   ├── MidasApiClient.kt       # REST client (Ktor)
│       │   │   │   ├── MidasWebSocket.kt        # WebSocket client para streaming audio
│       │   │   │   ├── models/                  # DTOs
│       │   │   │   └── AuthInterceptor.kt       # X-API-Key header
│       │   │   └── repository/
│       │   │       ├── ConversationRepository.kt
│       │   │       ├── ApplicationRepository.kt
│       │   │       ├── CallRepository.kt
│       │   │       └── AdvisorRepository.kt
│       │   ├── domain/
│       │   │   ├── model/                       # Conversation, Application, CallRecording
│       │   │   └── usecase/                     # ListConversations, StartRecording, etc.
│       │   └── audio/
│       │       └── AudioRecorder.kt             # expect class — interfaz multiplataforma
│       │
│       ├── iosMain/
│       │   └── audio/
│       │       └── AudioRecorder.ios.kt         # actual: AVAudioEngine / AVAudioRecorder
│       │
│       ├── androidMain/
│       │   └── audio/
│       │       └── AudioRecorder.android.kt     # actual: AudioRecord API
│       │
│       └── desktopMain/
│           └── audio/
│               └── AudioRecorder.desktop.kt     # actual: javax.sound / TargetDataLine
│
├── composeApp/                      # Compose Multiplatform — UI compartida
│   └── src/commonMain/
│       ├── App.kt
│       ├── theme/
│       ├── navigation/
│       └── ui/
│           ├── auth/                # Login
│           ├── dashboard/           # Resumen, stats
│           ├── conversations/       # Lista + detalle
│           ├── applications/        # Lista + detalle
│           └── calls/
│               ├── CallListScreen.kt        # Lista de grabaciones
│               ├── RecordingScreen.kt       # Grabación en vivo + transcripción en tiempo real
│               └── CallDetailScreen.kt      # Detalle: transcripción + intención + solicitud
│
├── iosApp/                          # iOS entry point
├── androidApp/                      # Android entry point
└── desktopApp/                      # Desktop entry point
```

### Audio Recording: expect/actual por plataforma

```kotlin
// commonMain — interfaz compartida
expect class AudioRecorder {
    fun start(onChunk: (ByteArray) -> Unit)   // Emite chunks de audio PCM
    fun stop()
    fun isRecording(): Boolean
}

// iosMain — AVAudioEngine captura audio del mic
actual class AudioRecorder { /* AVAudioEngine con tap en input node */ }

// androidMain — AudioRecord API
actual class AudioRecorder { /* AudioRecord con buffer read loop */ }
```

Los chunks de audio (PCM 16kHz mono) se envían por WebSocket al backend.

---

## Cambios en el API Backend

### Nuevos endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/calls` | Crear sesión de grabación (retorna call_id) |
| `WS` | `/api/calls/{id}/stream` | WebSocket: recibir audio, enviar transcripción en vivo |
| `POST` | `/api/calls/{id}/end` | Finalizar grabación, ejecutar detección de intención |
| `GET` | `/api/calls` | Listar grabaciones del asesor |
| `GET` | `/api/calls/{id}` | Detalle (transcripción, intención, solicitud) |
| `POST` | `/api/calls/{id}/generate-application` | Generar solicitud desde la llamada |
| `GET` | `/api/advisors/me/stats` | Estadísticas resumen |

### WebSocket: Protocolo de streaming

```
Cliente → Servidor:
  - Binary frames: chunks de audio PCM (16kHz, 16-bit, mono)
  - Text frame: {"action": "end"} para finalizar

Servidor → Cliente:
  - Text frames: {"type": "transcript", "text": "...", "is_final": false}
  - Text frames: {"type": "transcript", "text": "...", "is_final": true}
  - Text frame: {"type": "completed", "intent": {...}, "call_id": "..."}
```

### Nuevo Bounded Context: `call/`

```
src/call/
├── domain/
│   ├── entities.py          # CallRecording (id, advisor_id, client_name, status, transcript, duration)
│   ├── value_objects.py     # CallId, CallStatus (RECORDING → PROCESSING → COMPLETED | FAILED)
│   ├── ports.py             # CallRepository, SpeechTranscriber
│   └── exceptions.py
├── application/
│   └── use_cases.py         # StartCall, EndCall, ListCalls, GetCall
└── infrastructure/
    ├── api.py               # WebSocket + REST endpoints
    ├── google_stt_adapter.py  # Google Cloud Speech-to-Text v2 streaming
    ├── adapters.py          # InMemoryCallRepository
    ├── postgres_adapter.py
    └── schemas.py
```

### SpeechTranscriber Port

```python
# src/call/domain/ports.py
class SpeechTranscriber(ABC):
    @abstractmethod
    async def transcribe_stream(
        self, audio_chunks: AsyncIterator[bytes]
    ) -> AsyncIterator[TranscriptChunk]:
        """Recibe stream de audio, emite chunks de transcripción."""
        ...
```

Implementado con Google Cloud Speech-to-Text v2 streaming API (locale `es`, soporta variantes LatAm).

### Integración con IntentDetector existente

Al finalizar la llamada, la transcripción completa se convierte a `Conversation`:
- Speaker diarization de STT separa hablantes → `Message` con `is_advisor` por hablante
- Se pasa al `IntentDetector.detect()` existente sin cambios
- Si hay intención → mismo flujo de `generate-application`

### Nueva migración

```sql
-- 0004_create_call_recordings.sql
CREATE TABLE call_recordings (
    id UUID PRIMARY KEY,
    advisor_id UUID NOT NULL REFERENCES advisors(id),
    client_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'recording',
    transcript TEXT,
    duration_seconds INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_call_recordings_advisor_id ON call_recordings (advisor_id);
```

### Archivos a modificar en backend

| Archivo | Cambio |
|---------|--------|
| `src/main.py` | Registrar `call/` router, agregar `SpeechTranscriber` al composition root |
| `pyproject.toml` | Agregar `google-cloud-speech>=2.0` |
| `migrations/` | Agregar `0004_create_call_recordings.sql` |
| `.env.example` | Agregar `GOOGLE_CLOUD_PROJECT` |

---

## Plan de Implementación

### Fase 1: Backend — Bounded Context `call/` + WebSocket (3-4 días)
1. Dominio: `CallRecording`, `CallId`, `CallStatus`, `CallRepository`, `SpeechTranscriber` port
2. Use cases: `StartCall`, `EndCall`, `ListCalls`, `GetCall` + tests TDD
3. `InMemoryCallRepository`
4. Migración `0004_create_call_recordings.sql` + `PostgresCallRepository`
5. `GoogleSpeechTranscriber` adapter (Speech-to-Text v2 streaming, español)
6. Endpoint WebSocket `/api/calls/{id}/stream` — recibe audio, envía transcripción
7. REST endpoints: `POST /api/calls`, `POST /api/calls/{id}/end`, `GET /api/calls`, `GET /api/calls/{id}`
8. Integración con `IntentDetector` al finalizar llamada
9. `GET /api/advisors/me/stats`
10. Registrar router en `src/main.py`

### Fase 2: Proyecto KMP — Setup + API Client (2-3 días)
1. Crear `apps/mobile/` con template KMP + Compose Multiplatform
2. Gradle: shared + composeApp + iosApp + androidApp + desktopApp
3. Ktor HTTP client + WebSocket client en shared
4. `MidasApiClient` con todos los endpoints REST
5. `MidasWebSocket` para streaming de audio/transcripción
6. `AuthInterceptor` (X-API-Key)
7. Modelos de dominio compartidos

### Fase 3: Audio Recording multiplataforma (2-3 días)
1. `expect/actual AudioRecorder` — captura PCM 16kHz mono
2. iOS: `AVAudioEngine` con tap en input node
3. Android: `AudioRecord` API con buffer loop
4. Desktop: `javax.sound.sampled.TargetDataLine`
5. Tests: verificar que emite chunks del tamaño correcto

### Fase 4: Auth + Dashboard UI (2-3 días)
1. Login: email → `GET /api/advisors/me` → guardar API key (DataStore)
2. Dashboard: stats, actividad reciente
3. Lista de conversaciones + detalle (mensajes, intención)
4. Lista de solicitudes + detalle
5. Navegación

### Fase 5: Grabación en Vivo UI (3-4 días)
1. `RecordingScreen`: botón grabar, nombre del cliente, timer
2. Conectar `AudioRecorder` → WebSocket → mostrar transcripción en tiempo real
3. Al detener: mostrar resultado de intención
4. Botón "Generar solicitud" → mostrar solicitud generada
5. `CallListScreen`: historial de grabaciones
6. `CallDetailScreen`: transcripción completa + intención + solicitud

### Fase 6: Polish + Deploy (2-3 días)
1. Manejo de errores (mic denegado, conexión perdida, audio pobre)
2. Persistencia local (DataStore) para settings y cache
3. Build iOS → TestFlight
4. Build Android → APK/AAB
5. Build desktop

---

## Dependencias técnicas

### Backend (Python)
| Librería | Uso |
|----------|-----|
| `google-cloud-speech` | Speech-to-Text v2 streaming |

### App (Kotlin)
| Librería | Uso |
|----------|-----|
| Ktor Client | HTTP + WebSocket multiplataforma |
| Kotlinx Serialization | JSON |
| Kotlinx Coroutines | Async, Flow para audio streaming |
| Compose Multiplatform | UI compartida |
| Compose Navigation | Navegación type-safe |
| DataStore | Persistencia local |
| expect/actual | Audio recording nativo por plataforma |

---

## Decisiones clave

| Decisión | Elegida | Alternativa | Por qué |
|----------|---------|-------------|---------|
| Framework | KMP + Compose Multiplatform | Flutter | Futuro path a interceptación nativa de llamadas en Android (Kotlin nativo). iOS-first pero KMP estable desde mayo 2025 |
| Captura audio | Micrófono en vivo durante llamada | Dictado post-llamada | El usuario quiere transcripción en tiempo real, no resumen post-facto |
| Speech-to-Text | Google Cloud STT v2 streaming | On-device (SFSpeechRecognizer) | Streaming de calidad, speaker diarization, consistente en todas las plataformas |
| Extensión Chrome | Coexiste con la app | App reemplaza todo | Productos complementarios: Chrome para WhatsApp Web, app para llamadas + dashboard |
| Audio storage | Solo transcripción, no audio | Guardar audio en GCS | Simplifica privacy/legal. Audio no sale del dispositivo, solo texto |

---

Última actualización: 2026-04-10

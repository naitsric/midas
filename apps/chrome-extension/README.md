# MIDAS Chrome Extension

Extensión Chrome (Manifest V3) que captura pasivamente conversaciones de WhatsApp Web, detecta intención financiera via IA, y genera solicitudes de crédito pre-llenadas para asesores financieros independientes en LatAm.

## Modelo mental

La extensión funciona como un **asistente pasivo** que observa WhatsApp Web. No modifica la interfaz de WhatsApp ni envía mensajes. Solo lee, analiza, y presenta resultados en un panel lateral propio.

```
WhatsApp Web DOM
       |
       | (lee mensajes visibles)
       v
Content Script (scraper)
       |
       | chrome.runtime.sendMessage
       v
Service Worker (orquestador)
       |
       | fetch() → API Backend
       v
MIDAS API (localhost:8000)
       |
       | respuesta
       v
Service Worker
       |
       | chrome.runtime.sendMessage
       v
Content Script (sidebar UI)
```

## Arquitectura

### Capas y responsabilidades

```
chrome-extension/
├── manifest.json              # Configuración MV3
├── src/
│   ├── background/
│   │   └── service-worker.js  # Orquestador central (no persistent)
│   ├── content/
│   │   ├── scraper.js         # Lee mensajes del DOM de WhatsApp Web
│   │   ├── sidebar.js         # Inyecta y maneja el panel lateral MIDAS
│   │   └── main.js            # Entry point del content script
│   ├── popup/
│   │   ├── popup.html         # UI de configuración (API key, estado)
│   │   ├── popup.js           # Lógica del popup
│   │   └── popup.css          # Estilos del popup
│   ├── services/
│   │   ├── api-client.js      # Cliente HTTP para el backend MIDAS
│   │   └── storage.js         # Wrapper sobre chrome.storage.local
│   └── styles/
│       └── sidebar.css        # Estilos del panel lateral
└── icons/                     # Iconos de la extensión (16, 48, 128px)
```

### 1. Service Worker (`background/service-worker.js`)

**Rol**: Orquestador central. Coordina la comunicación entre content scripts y el backend API.

**Responsabilidades**:
- Recibir mensajes del content script via `chrome.runtime.onMessage`
- Hacer llamadas HTTP al backend MIDAS (`api-client.js`)
- Gestionar el estado de autenticación (API key en `chrome.storage.local`)
- Reenviar resultados al content script

**No hace**:
- No toca el DOM (no tiene acceso)
- No persiste estado propio (es non-persistent en MV3, se puede matar en cualquier momento)

**Mensajes que maneja**:

| Mensaje | Origen | Acción |
|---------|--------|--------|
| `IMPORT_CONVERSATION` | content script | POST `/api/conversations/import` |
| `DETECT_INTENT` | content script | POST `/api/conversations/{id}/detect-intent` |
| `GENERATE_APPLICATION` | content script | POST `/api/conversations/{id}/generate-application` |
| `GET_AUTH_STATUS` | popup/content | Verifica si hay API key almacenada |
| `SAVE_API_KEY` | popup | Guarda API key, valida con GET `/api/advisors/me` |
| `LOGOUT` | popup | Elimina API key del storage |

### 2. Content Script — Scraper (`content/scraper.js`)

**Rol**: Lee mensajes del chat activo en WhatsApp Web.

**Cómo funciona**:
- Usa `MutationObserver` para detectar cuándo cambia el chat activo
- Lee los mensajes visibles del DOM (selector de WhatsApp para burbujas de mensaje)
- Extrae: texto del mensaje, nombre del remitente, si es incoming/outgoing
- Determina quién es el asesor (outgoing) y quién es el cliente (incoming)
- Agrupa los mensajes en una conversación estructurada

**Selectores clave de WhatsApp Web**:
- Los selectores del DOM de WhatsApp cambian frecuentemente. Centralizar aquí para facilitar mantenimiento.
- Usar `data-testid` attributes cuando estén disponibles (más estables que clases CSS)

**Detección de cambio de chat**:
- Observer en el header del chat (nombre del contacto)
- Cuando cambia, se capturan los mensajes del nuevo chat

**Output**: Objeto con estructura:
```javascript
{
  advisor_name: "Carlos",     // nombre del usuario de WhatsApp
  client_name: "María",       // nombre del contacto
  messages: [
    { sender_name: "Carlos", is_advisor: true, text: "Hola María" },
    { sender_name: "María", is_advisor: false, text: "Necesito un crédito..." },
  ]
}
```

### 3. Content Script — Sidebar (`content/sidebar.js`)

**Rol**: Panel lateral inyectado en WhatsApp Web que muestra resultados de MIDAS.

**Estados del sidebar**:

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐     ┌──────────────┐
│  NO_AUTH     │────>│  IDLE        │────>│  ANALYZING    │────>│  RESULT      │
│ (pedir key) │     │ (esperando)  │     │ (procesando)  │     │ (intención)  │
└─────────────┘     └──────────────┘     └───────────────┘     └──────────────┘
                                                                      │
                                                                      v
                                                               ┌──────────────┐
                                                               │  APPLICATION │
                                                               │ (solicitud)  │
                                                               └──────────────┘
```

- **NO_AUTH**: No hay API key configurada. Muestra instrucciones para configurar en el popup.
- **IDLE**: Autenticado, esperando que se abra un chat.
- **ANALYZING**: Se envió la conversación al backend, esperando respuesta.
- **RESULT**: Muestra intención detectada (producto, confianza, entidades).
- **APPLICATION**: Muestra solicitud pre-llenada generada.

**Interacciones del usuario**:
- Botón "Analizar conversación" → dispara scraping + import + detect-intent
- Botón "Generar solicitud" → dispara generate-application (solo si intención es actionable)
- Los resultados se muestran en el mismo sidebar

### 4. Popup (`popup/`)

**Rol**: Configuración y estado de la extensión. Se abre al hacer clic en el icono de MIDAS.

**Funcionalidad**:
- Input para ingresar API key del asesor
- Botón "Conectar" que valida la key contra `GET /api/advisors/me`
- Muestra estado: conectado/desconectado, nombre del asesor
- Botón "Desconectar" para eliminar la key

**No muestra**: resultados de análisis ni solicitudes (eso va en el sidebar).

### 5. API Client (`services/api-client.js`)

**Rol**: Cliente HTTP que encapsula la comunicación con el backend MIDAS.

**Métodos**:
```javascript
class MidasApiClient {
  constructor(baseUrl, apiKey)

  // Advisor
  async getMe()                          // GET /api/advisors/me

  // Conversations
  async importConversation(data)         // POST /api/conversations/import
  async listConversations()              // GET /api/conversations
  async getConversation(id)              // GET /api/conversations/{id}

  // Intent
  async detectIntent(conversationId)     // POST /api/conversations/{id}/detect-intent

  // Applications
  async generateApplication(convId)      // POST /api/conversations/{id}/generate-application
  async listApplications()               // GET /api/applications
  async getApplication(id)               // GET /api/applications/{id}
}
```

- Todos los métodos agregan `X-API-Key` header automáticamente
- Base URL configurable (default: `http://localhost:8000`)
- Manejo de errores: 401 → marca como desautenticado, otros → propaga error

### 6. Storage (`services/storage.js`)

**Rol**: Wrapper sobre `chrome.storage.local` con keys tipadas.

**Keys almacenadas**:
```javascript
{
  "midas_api_key": "midas_abc123...",       // API key del asesor
  "midas_api_url": "http://localhost:8000", // URL del backend
  "midas_advisor_name": "Carlos Pérez",     // Cache del nombre (para UI)
}
```

## Flujos principales

### Flujo 1: Primer uso (autenticación)

```
1. Asesor instala la extensión
2. Abre WhatsApp Web → sidebar muestra estado NO_AUTH
3. Click en icono MIDAS → popup pide API key
4. Asesor pega su key → popup llama SAVE_API_KEY
5. Service Worker valida con GET /api/advisors/me
6. Si ok → guarda key + nombre en storage, sidebar pasa a IDLE
7. Si falla → muestra error en popup
```

### Flujo 2: Análisis de conversación

```
1. Asesor abre un chat en WhatsApp Web
2. Click "Analizar" en sidebar → sidebar pasa a ANALYZING
3. Scraper extrae mensajes del DOM
4. Content script envía IMPORT_CONVERSATION al service worker
5. Service worker hace POST /api/conversations/import
6. Retorna conversation_id
7. Service worker hace POST /api/conversations/{id}/detect-intent
8. Retorna resultado de intención
9. Sidebar muestra resultado (RESULT)
   - Si actionable: botón "Generar solicitud" visible
   - Si no: muestra "No se detectó intención financiera"
```

### Flujo 3: Generación de solicitud

```
1. Desde estado RESULT con intención actionable
2. Click "Generar solicitud"
3. Service worker hace POST /api/conversations/{id}/generate-application
4. Sidebar muestra solicitud pre-llenada (APPLICATION)
   - Datos del solicitante
   - Producto solicitado
   - Resumen de la conversación
```

## Consideraciones técnicas

### Selectores DOM de WhatsApp Web

WhatsApp Web actualiza sus selectores frecuentemente (ofuscación de clases CSS). Estrategia:

1. Preferir `data-testid` attributes (más estables)
2. Centralizar todos los selectores en un objeto de constantes
3. Si un selector falla, mostrar error descriptivo en sidebar (no crashear silenciosamente)

### Service Worker lifecycle (MV3)

- Chrome puede matar el service worker después de ~30s de inactividad
- No guardar estado en variables globales — usar `chrome.storage`
- Las llamadas API pueden tardar — si el worker muere mid-request, el content script debe reintentar

### Seguridad

- La API key se almacena en `chrome.storage.local` (encriptado por Chrome, accesible solo por la extensión)
- Solo se envían requests al backend configurado (localhost en dev)
- No se envía data a ningún otro destino
- El contenido de las conversaciones solo viaja al backend MIDAS propio del asesor

### Consent

- La extensión solo captura cuando el asesor hace click en "Analizar" (no automáticamente)
- El asesor es responsable de tener consentimiento del cliente antes de analizar
- Futuro: mostrar disclosure text en el sidebar antes de cada análisis

Última actualización: 2026-04-09

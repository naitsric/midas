# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is this repo

MIDAS monorepo containing **documentation** (strategy, product, architecture) and **early-stage application code** (Chrome extension + API backend). Designed to be consumed by LLMs and humans alike.

## What is MIDAS

MIDAS es una plataforma de **WhatsApp Conversation Intelligence para asesores financieros independientes en LatAm**. Captura pasivamente conversaciones de WhatsApp, detecta intención financiera por IA, y genera automáticamente solicitudes de crédito pre-llenadas. Ningún competidor directo integra estas tres capas en LatAm.

## Repository structure

```
midas/
├── apps/
│   ├── api-backend/       # FastAPI + Google Gemini — intent detection API
│   └── chrome-extension/  # Manifest V3 extension for WhatsApp Web
├── architecture/          # Architecture docs, ADRs, diagrams
├── company/               # Vision, mission, glossary
├── product/               # Product specs, features, competitive landscape
├── guides/                # Contributing guide
└── infrastructure/        # Cloud/CI/CD docs (placeholder)
```

## Development commands

### API Backend (`apps/api-backend/`)

```bash
# Install dependencies (uses uv, Python >=3.12)
cd apps/api-backend && uv sync

# Run the dev server
uv run uvicorn src.main:app --reload --port 8000

# --- Pirámide de tests ---

# Unit tests (default, CI — rápido, sin servicios externos)
uv run pytest                          # Corre solo unit tests por defecto
uv run pytest tests/conversation/ -q   # Bounded context: Conversation
uv run pytest tests/intent/ -q         # Bounded context: Intent

# Integration tests (requiere GEMINI_API_KEY en .env)
uv run pytest -m integration           # Gemini real, DB real (cuando exista)

# E2E tests (flujo completo: API + Gemini real)
uv run pytest -m e2e                   # Chrome Extension → API → Gemini

# Combinaciones
uv run pytest -m 'not e2e'             # Unit + Integration (CI con secrets)
uv run pytest -m 'integration or e2e'  # Solo tests que requieren servicios

# Lint and format
uv run ruff check .
uv run ruff format .
```

Requires `GEMINI_API_KEY` in `.env` file at `apps/api-backend/.env` (see `.env.example`).

### Chrome Extension (`apps/chrome-extension/`)

No build step. Load as unpacked extension in Chrome:
1. Go to `chrome://extensions/`
2. Enable Developer Mode
3. "Load unpacked" → select `apps/chrome-extension/`

The extension connects to the API backend at `http://localhost:8000`.

## Conventions

- **Idioma de comunicación**: siempre responder y comunicarse en **español** en este repositorio
- **File names**: English, kebab-case
- **Content language**: Spanish or English, consistent within each document
- **Commit prefix**: always `docs:` for documentation changes
- **Branch naming**: `docs/<tema>`
- **Document template**: every `.md` must have: Título, Contexto, Contenido, Referencias, and `Última actualización: YYYY-MM-DD` at the bottom
- **Diagrams**: Mermaid in fenced code blocks. Only `.png`/`.svg` in `architecture/diagrams/` when Mermaid can't express it
- **ADRs**: `architecture/adrs/NNNN-titulo-en-kebab-case.md` with Status/Contexto/Decisión/Consecuencias sections
- **One file = one topic**. Split documents beyond ~300 lines

## Product context

- **Etapa**: pre-prototipo (investigación y diseño, primeros prototipos funcionales)
- **Mercado inicial**: Colombia (42% YoY en WhatsApp Business API, red de 1,000+ asesores vía LQN/Vehicredi)
- **Vertical inicial**: hipotecas y crédito vehicular
- **Modelo de negocio**: gratis para el asesor, cobro al originador/lender por solicitud calificada (performance-based)
- **Modelo técnico**: extensión Chrome sobre WhatsApp Web (modelo Cooby)
- **Positioning**: "asistente automático" (no monitoreo silencioso). El asesor instala voluntariamente

## Key domain concepts

- **Asesores financieros independientes**: contratistas que venden productos financieros usando WhatsApp personal. Su cartera de clientes ES su negocio.
- **Captura pasiva**: la extensión Chrome lee conversaciones de WhatsApp Web sin data entry manual. Requiere consentimiento de ambas partes.
- **Detección de intención financiera**: IA analiza conversaciones para identificar necesidad de un producto financiero específico.
- **Solicitud pre-llenada**: solicitud de crédito generada automáticamente con datos extraídos de la conversación. Es el "aha moment" del producto.
- **Consent mechanism**: disclosure obligatorio al cliente antes de captura.

## Legal constraints (riesgo existencial si se ignoran)

- **Colombia (Ley 1581/2012 + Ley 1266/2008)**: consentimiento previo, expreso e informado. Datos financieros con estatus especial. Multas hasta ~$500K USD.
- **México (LFPDPPP)**: consentimiento expreso para datos financieros. Incluye penalidades criminales.
- **Ambas jurisdicciones**: se requiere consentimiento de ambas partes (asesor Y cliente).

## Technical stack

- **API Backend**: Python 3.12+, FastAPI, Google Gemini (intent detection), Pydantic, uv package manager
- **Chrome Extension**: Vanilla JS, Manifest V3, content script injected on `web.whatsapp.com`
- **API ↔ Extension**: REST, CORS enabled
- **Testing**: pytest, pytest-asyncio, httpx (TestClient)
- **Linting/Format**: ruff

## Architecture (API Backend)

**Hexagonal (Ports & Adapters) + DDD + TDD**

```
src/
├── conversation/              # Bounded Context: Conversation
│   ├── domain/                # Entities, Value Objects, Ports, Exceptions
│   ├── application/           # Use cases (SaveConversation, GetConversation)
│   └── infrastructure/        # Adapters (InMemoryRepo, FastAPI router, schemas)
├── intent/                    # Bounded Context: Intent Detection
│   ├── domain/                # IntentResult, Confidence, ProductType, IntentDetector port
│   ├── application/           # Use cases (DetectFinancialIntent)
│   └── infrastructure/        # GeminiIntentDetector adapter, FastAPI router, schemas
└── main.py                    # Composition root — dependency injection via create_app()
```

- **Domain layer**: sin dependencias externas (solo stdlib + Pydantic). Value objects inmutables (`@dataclass(frozen=True)`)
- **Ports**: interfaces abstractas (ABC). Adapters: implementaciones concretas
- **Composition root**: `create_app()` acepta repos y detectors inyectados — para tests se pasan fakes, en producción se usan los reales
- **TDD**: tests primero (RED), implementación después (GREEN), refactor

### API Endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/health` | Health check |
| `POST` | `/api/conversations` | Crear conversación |
| `GET` | `/api/conversations/{id}` | Obtener conversación |
| `POST` | `/api/conversations/{id}/messages` | Agregar mensaje |
| `POST` | `/api/conversations/{id}/detect-intent` | Detectar intención financiera |

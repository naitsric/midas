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
│   ├── api-backend/       # FastAPI + Google Gemini — multi-tenant API (intent detection, credit applications)
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
uv run pytest tests/advisor/ -q        # Bounded context: Advisor
uv run pytest tests/application/ -q    # Bounded context: Application

# Integration tests (requiere GEMINI_API_KEY en .env)
uv run pytest -m integration           # Gemini real, DB real (cuando exista)

# E2E tests (flujo completo: API + Gemini real)
uv run pytest -m e2e                   # Chrome Extension → API → Gemini

# Combinaciones
uv run pytest -m 'not e2e'             # Unit + Integration (CI con secrets)
uv run pytest -m 'integration or e2e'  # Solo tests que requieren servicios

# --- Database ---

# Migraciones (requiere DATABASE_URL en .env)
uv run python -m src.shared.infrastructure.migrator run      # Ejecutar pendientes
uv run python -m src.shared.infrastructure.migrator status   # Ver estado

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

- **Multi-tenancy por asesor**: cada asesor es un tenant aislado. Toda entidad (conversación, solicitud) pertenece a un `AdvisorId`. Los repositorios exponen `find_by_id_and_advisor()` para garantizar aislamiento — un asesor nunca ve datos de otro.
- **Autenticación por API Key**: cada asesor recibe un `ApiKey` (prefijo `midas_*`) al registrarse. Todos los endpoints (excepto registro y health) requieren `X-API-Key` header. El dependency `RequireAdvisor` inyecta el asesor autenticado en cada handler.
- **Asesores financieros independientes**: contratistas que venden productos financieros usando WhatsApp personal. Su cartera de clientes ES su negocio.
- **Ciclo de vida del asesor**: ACTIVE → SUSPENDED (con razón) → ACTIVE, o ACTIVE → DEACTIVATED (terminal). Solo asesores ACTIVE pueden autenticarse.
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

**Hexagonal (Ports & Adapters) + DDD + TDD + Multi-tenant**

```
src/
├── advisor/                   # Bounded Context: Advisor (tenant identity + auth)
│   ├── domain/                # Advisor entity, AdvisorId/ApiKey/AdvisorStatus VOs, ports
│   ├── application/           # Use cases (RegisterAdvisor, AuthenticateAdvisor, GetAdvisor)
│   └── infrastructure/        # InMemoryRepo, RequireAdvisor auth dependency, FastAPI router
├── conversation/              # Bounded Context: Conversation (scoped by AdvisorId)
│   ├── domain/                # Entities, Value Objects, Ports, Exceptions
│   ├── application/           # Use cases (SaveConversation, GetConversation)
│   └── infrastructure/        # Adapters (InMemoryRepo, FastAPI router, schemas)
├── intent/                    # Bounded Context: Intent Detection
│   ├── domain/                # IntentResult, Confidence, ProductType, IntentDetector port
│   ├── application/           # Use cases (DetectFinancialIntent)
│   └── infrastructure/        # GeminiIntentDetector adapter, FastAPI router, schemas
├── application/               # Bounded Context: Credit Application (scoped by AdvisorId)
│   ├── domain/                # CreditApplication entity, Applicant/ProductRequest VOs, ports
│   ├── application/           # Use cases (GenerateCreditApplication)
│   └── infrastructure/        # GeminiApplicationGenerator, InMemoryRepo, FastAPI router
└── main.py                    # Composition root — dependency injection via create_app()
```

- **Multi-tenant por diseño**: el `AdvisorId` es el tenant key. Todas las entidades llevan `advisor_id`. Los repository ports definen `find_by_id_and_advisor(entity_id, advisor_id)` y `find_all_by_advisor(advisor_id)` — no existe query sin scope de tenant.
- **Auth como bounded context**: `RequireAdvisor` (FastAPI Depends) valida `X-API-Key`, resuelve el `Advisor` activo, y lo inyecta en todos los handlers. El registro (`POST /api/advisors`) es el único endpoint público.
- **Domain layer**: sin dependencias externas (solo stdlib + Pydantic). Value objects inmutables (`@dataclass(frozen=True)`)
- **Ports**: interfaces abstractas (ABC). Adapters: implementaciones concretas
- **Composition root**: `create_app()` acepta repos, detectors y generators inyectados — para tests se pasan fakes, en producción se usan los reales
- **TDD**: tests primero (RED), implementación después (GREEN), refactor

### API Endpoints

| Método | Endpoint | Auth | Descripción |
|--------|----------|------|-------------|
| `GET` | `/health` | No | Health check |
| `POST` | `/api/advisors` | No | Registrar asesor (devuelve API key) |
| `GET` | `/api/advisors/me` | Si | Perfil del asesor autenticado |
| `GET` | `/api/conversations` | Si | Listar conversaciones del asesor |
| `POST` | `/api/conversations` | Si | Crear conversación |
| `POST` | `/api/conversations/import` | Si | Importar conversación completa con mensajes |
| `GET` | `/api/conversations/{id}` | Si | Obtener conversación |
| `POST` | `/api/conversations/{id}/messages` | Si | Agregar mensaje |
| `POST` | `/api/conversations/{id}/detect-intent` | Si | Detectar intención financiera |
| `POST` | `/api/conversations/{id}/generate-application` | Si | Generar solicitud de crédito |
| `GET` | `/api/applications` | Si | Listar solicitudes del asesor |
| `GET` | `/api/applications/{id}` | Si | Obtener solicitud |

# MIDAS — Architecture Overview

> High-level technical architecture for MIDAS Global Conversation Intelligence using Google Cloud.

## Context

MIDAS is in the pre-prototype stage. This document describes the proposed technical architecture using **Python (FastAPI)** and the **Gemini 2.5/3.1 Flash** series for enterprise-grade conversation intelligence.

## Technical Model: Browser-Native (Chrome Extension)

The primary anchor for MIDAS is a **Chrome extension on top of WhatsApp Web**. This acts as a zero-friction UI layer that synchronizes conversations with a **Python-powered** AI backend.

### Why this approach?
- **Zero-Behavior Change**: Advisors keep their current workflow.
- **Privacy & Consent**: Explicit voluntary installation and automated disclosure injection.
- **Next-Gen AI**: Leveraging **Gemini 2.5 Flash** (high-speed data extraction) and **Gemini 3.1** (advanced multimodal reasoning).

## High-Level Architecture (Google Cloud Stack)

```mermaid
graph LR
    subgraph "Advisor Desktop (Phase 1)"
        WW[WhatsApp Web]
        CE[MIDAS Chrome Extension]
    end

    subgraph "Advisor Mobile (Phase 3)"
        AN[Android Notification Listener]
        AR[Audio Recording Service]
    end

    subgraph "Google Cloud Platform"
        API[API Gateway - FastAPI (Python)]
        
        subgraph "AI Intelligence Layer"
            GE[Gemini 2.5 Flash / 3.1 Pro]
            STT[Google Cloud Speech-to-Text]
        end

        subgraph "Data & Memory"
            VS[(Vertex AI Vector Search)]
            DB[(Cloud Firestore)]
        end

        subgraph "Application Logic"
            AG[Application Generator]
            IM[Integration Manager]
        end
    end

    subgraph "External Ecosystem"
        OR[Lenders / Origination Platforms]
    end

    WW --> CE
    CE --> API
    AN --> API
    AR --> STT
    STT --> API
    
    API --> GE
    GE --> VS
    GE --> AG
    AG --> IM
    IM --> OR
    API --> DB
```

## Multi-Tenant Architecture

MIDAS es **multi-tenant por diseño**: cada asesor financiero es un tenant aislado.

- **Tenant key**: `AdvisorId` (UUID). Todas las entidades (Conversation, CreditApplication) llevan `advisor_id` como campo obligatorio.
- **Aislamiento en repositorios**: los ports definen `find_by_id_and_advisor(entity_id, advisor_id)` — no existe query sin scope de tenant. Un asesor nunca puede acceder a datos de otro.
- **Autenticación**: API Key única por asesor (`X-API-Key` header, prefijo `midas_*`). `RequireAdvisor` resuelve el asesor activo e inyecta su identidad en todos los handlers.
- **Ciclo de vida**: ACTIVE → SUSPENDED → ACTIVE, o ACTIVE → DEACTIVATED (terminal). Solo asesores activos pueden autenticarse.

```mermaid
graph TD
    EXT[Chrome Extension] -->|X-API-Key| AUTH[RequireAdvisor]
    AUTH -->|Advisor activo| CONV[Conversation BC]
    AUTH -->|Advisor activo| APP[Application BC]
    AUTH -->|Advisor activo| INT[Intent BC]
    CONV -->|advisor_id scope| REPO_C[(ConversationRepo)]
    APP -->|advisor_id scope| REPO_A[(ApplicationRepo)]
```

## Core Components (The Google & Python Stack)

### 1. Conversation Intelligence Engine (`fastapi-backend`)
Powered by **Python (FastAPI)** and **Google AI SDK**:
- **Multi-Tenant API**: Each advisor operates in isolation with API key authentication. All data access is scoped by `AdvisorId`.
- **Intent Detection**: Using **Gemini 2.5 Flash** for real-time signal classification (Mortgages, Auto, etc.) due to its high-throughput and low latency.
- **Contextual Memory**: Leveraging Gemini's 1M+ token window to maintain long-term client history.
- **Python-Native AI Ecosystem**: Utilizing libraries like `pydantic` for robust data modeling and `langchain` for agentic workflows.

### 2. Long-Term Memory (`vertex-vector-search`)
- Stores conversational embeddings to retrieve historical context quickly.
- Ensures that if a client mentioned a specific property 3 months ago, the AI remembers it for the current application.

### 3. MIDAS Chrome Extension (`phase-1-anchor`)
- **Passive DOM Scraper**: Captures messages from WhatsApp Web without requiring the official (and costly) Business API.
- **In-Chat UI Injection**: Shows detected intents and "Review Application" buttons directly in the WhatsApp sidebar.

### 4. Application Generator (`app-generator`)
- Maps structured entities (Name, Amount, Term) extracted by Gemini into standardized financial JSON/PDF formats.
- Integrated with downstream platforms like **Blend** or **Creditas** via the Integration Manager.

## Roadmap & Evolution

- **Phase 1 (MVP)**: Chrome Extension + Python/FastAPI Backend + **Gemini 2.5 Flash** Text Analysis.
- **Phase 2 (Memory)**: Vertex AI Vector Search + Lender Integrations.
- **Phase 3 (Omnichannel)**: Android Notification Listener + Call Audio Transcription (Speech-to-Text).

## References
- [Product Roadmap](../product/overview.md)
- [ADR-0001: WhatsApp Capture Approach](./adrs/0001-whatsapp-capture-approach.md)

---
Última actualización: 2026-04-09

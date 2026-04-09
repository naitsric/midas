# MIDAS Documentation Hub

Central repository for **MIDAS Global Conversation Intelligence**. This serves as the **source of truth** for both the team and AI agents (LLMs) to understand the project's strategy, technical architecture, and product vision.

---

## What is MIDAS?

MIDAS is a **Global Conversation Intelligence** platform for financial services. It empowers financial advisors to eliminate administrative work by passively capturing and structuring professional conversations from WhatsApp and calls.

### Core Value Propositions:
- **Zero-Behavior Change**: No manual data entry or new systems to learn.
- **Conversation Intelligence**: AI intent detection and automated credit applications.
- **Instant "Aha Moment"**: Deliver tangible value (first application) within 24 hours of setup.

---

## Directory Structure

```
midas/
├── README.md                  # This file — general index and guidelines
├── CLAUDE.md                  # Guide for Claude Code
├── company/
│   ├── overview.md            # Vision, mission, and business model
│   └── glossary.md            # Domain terminology
├── product/
│   ├── overview.md            # Product vision and design decisions
│   ├── features/              # Feature specifications
│   ├── techspecs/             # Technical specifications
│   └── user-flows/            # Key user flows
├── architecture/
│   ├── overview.md            # Architectural overview
│   ├── services/              # Service components
│   ├── diagrams/              # Mermaid diagrams
│   ├── integrations/          # External integrations
│   └── adrs/                  # Architecture Decision Records
├── infrastructure/
│   └── overview.md            # Cloud, CI/CD, environments
└── guides/
    ├── onboarding.md          # Team onboarding guide
    └── contributing.md        # How to contribute to this repo
```

---

## How to use this repository with an LLM

### Full Context
To give an LLM a complete overview of MIDAS, provide these files in order:

1. `company/overview.md`
2. `product/overview.md`
3. `architecture/overview.md`

### Specific Context
If you need help with a particular service or feature, provide:

1. The relevant `overview.md` (Product or Architecture).
2. The specific service/feature file.
3. Related diagrams.

---

## Guidelines for Writing Documents

### 1. One file = One topic
Each `.md` must cover one well-defined topic. If it grows beyond ~300 lines, split it.

### 2. Consistent Structure
Follow the standard template (Title, One-line summary, Context, Content, References).

### 3. Write for LLMs (and Humans)
- **Be explicit.** Don't assume prior knowledge.
- **Use full names.** Mention specific services (e.g., `intent-detection-service`).
- **Include concrete examples.** Payloads, URLs, and commands.

### 4. Diagrams in Mermaid
Use [Mermaid](https://mermaid.js.org/) inside code blocks for versionable, renderable, and LLM-readable diagrams.

---

## Contribution Flow

1. **Create a branch**: `docs/<topic>`.
2. **Add or edit** files following the guidelines above.
3. **Open a PR** with a brief description of the changes.

---
Última actualización: 2026-04-08

# MIDAS — Product Overview & Roadmap

> Product vision, strategic phases, and prioritized design decisions.

## Context

MIDAS is in the pre-prototype stage. This document outlines the path from a zero-friction MVP to an omnichannel conversation intelligence platform.

## Product Vision

To become the global infrastructure layer for financial intent. We empower advisors by converting unstructured conversations into structured credit applications with **Zero-Behavior Change**.

---

## The 3-Phase Roadmap

### Phase 1: The Zero-Friction MVP (Chrome Extension)
**Objective**: Achieve the "Aha Moment" in < 24 hours via WhatsApp Web capture.
- **Anchor**: Browser-native extension (WhatsApp Web).
- **Core Intelligence**: **Google Gemini 1.5 Flash** for intent detection and entity extraction.
- **Value**: One-click generation of credit applications (Mortgage/Auto) directly within the WhatsApp UI.
- **Consent**: Automated disclosure injection in the first interaction.

### Phase 2: Intelligence & Ecosystem Integration
**Objective**: Build deep memory and connect to the global financial backend.
- **Infrastructure**: **Vertex AI Vector Search** for long-term client context.
- **Integrations**: Direct sync with Lenders/Originators (Blend, Creditas, etc.).
- **Productivity**: Dashboard for application tracking and lender feedback loops.
- **Refinement**: Self-learning feedback — advisor corrections train the AI.

### Phase 3: Omnichannel Capture (Mobile & Audio)
**Objective**: Capture intent wherever it happens (Calls & Mobile Notifications).
- **Mobile Anchor**: Android App with **Notification Listener API** (Passive Capture).
- **Audio Intelligence**: Integration with **Google Cloud Speech-to-Text** for call recording transcription.
- **Advanced AI**: Multimodal analysis of sentiment and complex financial intents across all channels.

---

## Ten Product Decisions — Pre-prototype

### 1. Zero-Behavior Change is the North Star
If the advisor has to change a single habit (like inviting a bot to a group or using a new number), the product has failed. **The extension model is non-negotiable for Phase 1.**

### 2. High-Frequency "Aha Moment"
Advisors must see their first pre-filled application within the first **24 hours**. Value must be tangible, immediate, and high-impact.

### 3. Built on Google Generative AI SDK
We prioritize speed and reasoning. Leveraging Gemini 1.5 Pro/Flash ensures we can handle complex financial intents and sub-second classification.

### 4. Consent as a Trust Feature
Disclosure is automated. The extension "suggests" or injects the consent message. Trust is maintained by keeping the capturing process visible but passive to the advisor.

### 5. Performance-Based Pricing
Free for the advisor. Pay-per-qualified-application for the lender. This removes financial friction and drives viral adoption among independent brokers.

### 6. Focus on Mortgage & Auto Loans
Start where the paperwork is the most painful and the transaction value is highest.

### 7. Global Category: Conversation Intelligence
MIDAS is not a CRM; it is an intelligence layer that *feeds* existing financial systems.

### 8. Mobile Bridge via Notifications
Instead of a "Bot in the Middle", we use a "Notification Listener" approach for mobile. This maintains privacy and zero-behavior change.

### 9. Downstream "Push" Model
Instead of a dashboard the advisor must visit, MIDAS "pushes" the finished application to the originators, mirroring how advisors work today.

### 10. Measure Trust over Usage
Success is defined by the advisor's willingness to keep the extension active on their client book.

## References
- [Company Overview](../company/overview.md)
- [Architecture Overview](../architecture/overview.md)
- [Competitive Landscape](./competitive-landscape.md)

---
Última actualización: 2026-04-08

# Feature Spec: Phase 1 — Chrome Extension (MVP)

> The zero-friction anchor for MIDAS Global Conversation Intelligence.

## Overview
A browser-native extension for WhatsApp Web that passively captures professional conversations and uses **Google Gemini 1.5** to detect financial intent and generate pre-filled credit applications.

## User Persona
Independent financial advisors (mortgage brokers, auto loan specialists) who primarily communicate with clients through personal WhatsApp and feel overwhelmed by administrative data entry.

---

## Core Capabilities

### 1. Passive Conversation Capture (Zero-Behavior Change)
- **DOM Scraper**: Continuously monitors active chat windows in WhatsApp Web.
- **Message Sync**: Only professional/financial keywords trigger sync to the backend to protect the advisor's personal privacy.
- **Selective Capture**: The advisor can toggle capture on/off for specific chats.

### 2. AI-Driven Intent Detection (Gemini 1.5 Flash)
- **Signal Monitoring**: AI identifies "buying signals" (e.g., "I'm looking for a house", "What are the current interest rates?").
- **Classification**: Categorizes the intent into specific financial products (Mortgage, Auto Loan, Refinance).
- **In-UI Alert**: A subtle "MIDAS" icon appears in the WhatsApp chat bar when an intent is detected.

### 3. Automated Application Generation
- **Entity Extraction**: Automatically extracts Name, Phone, Estimated Amount, Property Type, and Term from the conversation.
- **Application Draft**: Gemini generates a structured JSON/PDF application from the captured data.
- **The "2-Minute Review"**: The advisor reviews the draft within a sidebar in WhatsApp and makes manual corrections if necessary.

### 4. Consent & Trust Manager
- **Smart Injection**: Suggests an automated disclosure message for new clients: "This chat is monitored by my AI assistant to speed up your application."
- **Data Deletion**: Single-click option for the advisor to wipe captured data for any specific client.

---

## User Flow (The Phase 1 Experience)

1. **Setup (5 min)**: Advisor installs the extension and logs in with Google.
2. **Organic Work**: Advisor chats with a client about a home loan.
3. **Detection**: MIDAS sidebar highlights: *"Detected: Mortgage Intent (Amount: $250k)"*.
4. **Action**: Advisor clicks "Generate Application".
5. **Aha Moment**: A pre-filled mortgage application appears on the screen, ready to be reviewed and sent to the bank.

---

## Technical Stack (Phase 1)
- **Frontend**: Chrome Extension (React/TypeScript).
- **AI Engine**: Google AI SDK (Gemini 1.5 Flash).
- **Backend**: Cloud Run (Node.js/Go) for API Gateway.
- **Storage**: Firestore for transient application drafts.

---

## Success Metrics for Phase 1
- **Time Saved**: Reduce manual data entry from 45 min to < 5 min per application.
- **Time to Value**: First application generated in < 24 hours of installation.
- **Adoption Rate**: 90%+ of advisors who install the extension keep it active for > 7 days.

## References
- [Product Roadmap](../overview.md)
- [Architecture Overview](../../architecture/overview.md)

---
Última actualización: 2026-04-08

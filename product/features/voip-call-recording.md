# Feature Spec: VoIP Call Recording — AWS Chime + Telnyx

> Llamadas telefónicas reales desde la app MIDAS con grabación automática, transcripción y detección de intención financiera.

## Overview

Sistema VoIP que permite a los asesores financieros hacer y recibir llamadas PSTN (teléfono normal) directamente desde la app iOS de MIDAS. Cada llamada se graba automáticamente en servidor, se transcribe con Speech-to-Text, y se analiza con IA para detectar intención financiera — generando solicitudes de crédito pre-llenadas sin data entry manual.

## User Persona

Asesores financieros independientes en Colombia y México que hablan con clientes por teléfono diariamente y necesitan capturar información financiera de esas conversaciones sin interrumpir el flujo natural de la llamada.

---

## Core Capabilities

### 1. Llamadas Salientes (Outbound)
- **Desde la app**: El asesor selecciona un contacto o ingresa un número → presiona "Llamar"
- **CallKit nativo**: La llamada aparece como una llamada normal del iPhone (pantalla verde, controles estándar)
- **PSTN real**: El cliente recibe la llamada en su teléfono normal (celular o fijo)
- **Caller ID**: Se muestra el número local del asesor (+57 Colombia, +52 México)

### 2. Llamadas Entrantes (Inbound)
- **VoIP Push**: Cuando un cliente llama al número asignado del asesor, el iPhone suena con CallKit incluso si la app está cerrada
- **PushKit**: Notificación VoIP de baja latencia vía APNs despierta la app instantáneamente
- **Contestar/Rechazar**: Misma UX que una llamada telefónica normal

### 3. Grabación Automática en Servidor
- **Sin acceso al mic durante llamada**: iOS bloquea el micrófono durante llamadas activas — por eso la grabación ocurre en el servidor (Chime SMA), no en el dispositivo
- **Audio estéreo**: Canal izquierdo = asesor, canal derecho = cliente (facilita diarización)
- **Almacenamiento**: S3 con retención de 90 días, encriptado con SSE-S3
- **Formato**: WAV estéreo, 8kHz (estándar Chime SMA)

### 4. Transcripción Post-Llamada
- **Google Cloud Speech-to-Text v2**: Transcripción de alta calidad en español (variantes LatAm)
- **Speaker diarization**: Separa automáticamente lo que dijo el asesor vs. el cliente
- **Resultado**: Texto completo disponible en la app segundos después de colgar

### 5. Detección de Intención Financiera
- **Gemini AI**: Analiza la transcripción completa buscando señales de interés en productos financieros
- **Clasificación**: Hipoteca, crédito vehicular, refinanciamiento, etc.
- **Confianza**: Score de 0-1 indicando certeza de la detección
- **Accionable**: Si la intención es clara → botón "Generar solicitud" en la app

### 6. Generación Automática de Solicitud
- **Extracción de entidades**: Nombre, teléfono, monto estimado, tipo de propiedad, plazo — extraídos de la transcripción
- **Solicitud pre-llenada**: JSON estructurado con datos del cliente listo para enviar al originador
- **Revisión del asesor**: Pantalla de revisión con opción de corregir antes de enviar

---

## Arquitectura Técnica

### Stack

| Capa | Tecnología | Propósito |
|------|-----------|-----------|
| App iOS | Chime SDK iOS (WebRTC) + CallKit + PushKit | Audio VoIP + UX nativa de llamada |
| API Gateway | AWS HTTP API | Endpoints REST para control de llamadas |
| Call Control | Lambda (Node.js) | Crear meetings, gestionar attendees, triggear SMA |
| SMA Handler | Lambda (Node.js) | Routing PSTN, bridging, grabación |
| Recording Processor | Lambda (Node.js) | Procesar grabación S3, enviar a backend |
| Voice Connector | AWS Chime SDK Voice | SIP trunk termination hacia Telnyx |
| SIP Trunk | Telnyx | Números locales CO/MX, conectividad PSTN |
| Grabaciones | S3 | Almacenamiento temporal (90 días) |
| Estado | DynamoDB | Estado de llamadas activas (TTL 24h) |
| Push | SNS → APNs | VoIP push para llamadas entrantes |
| Backend | FastAPI + Google STT + Gemini | Transcripción + detección de intención |
| Base de datos | PostgreSQL | Persistencia de llamadas, transcripciones, solicitudes |

### Flujo Outbound (Asesor → Cliente)

```
1. iOS: POST /calls/dial {toNumber, clientName}
2. Lambda: CreateMeeting() + CreateAttendee() x2 + CreateSipMediaApplicationCall()
3. iOS: Join Meeting (WebRTC audio)
4. SMA: CallAndBridge → Voice Connector → Telnyx → PSTN
5. Cliente contesta → SMA: JoinChimeMeeting + StartCallRecording
6. Conversación: Asesor (WebRTC) ↔ Chime Meeting ↔ SMA (PSTN) ↔ Cliente
7. Hangup → StopCallRecording → Upload WAV a S3
8. S3 Event → Recording Processor → Backend: transcripción + intención
9. App muestra resultado: transcripción + solicitud generada
```

### Flujo Inbound (Cliente → Asesor)

```
1. Cliente marca número Telnyx del asesor
2. Telnyx → Voice Connector → SMA → Lambda
3. Lambda: CreateMeeting() + CreateAttendee() x2
4. Lambda: SNS → APNs VoIP Push → iPhone
5. iPhone: PushKit → CallKit reportNewIncomingCall()
6. Asesor contesta → Join Meeting (WebRTC)
7. SMA: JoinChimeMeeting + StartCallRecording
8. Conversación en curso (mismo que outbound paso 6-9)
```

---

## Decisiones de Diseño

### ¿Por qué VoIP y no grabación local del mic?
iOS bloquea el acceso al micrófono durante llamadas telefónicas activas. No hay API pública ni workaround para capturar audio de una llamada PSTN en curso. La única solución es que la llamada ocurra DENTRO de nuestra infraestructura VoIP, donde controlamos la grabación en el servidor.

### ¿Por qué AWS Chime SDK y no Twilio?
- **Costo**: Chime ~$0.008/min vs Twilio ~$0.013/min (40% más barato)
- **Flexibilidad**: SMA Lambda permite lógica custom sin límites de TwiML
- **Integración AWS**: S3, DynamoDB, SNS, CloudWatch — todo nativo
- **Chime Meeting bridge**: Permite mezclar WebRTC (app) + PSTN (cliente) en la misma sesión

### ¿Por qué Telnyx como SIP trunk?
AWS Chime no ofrece números telefónicos nativos para Colombia (+57) ni México (+52). Telnyx sí tiene cobertura LatAm con números locales y precios competitivos (~$0.007/min Colombia).

### ¿Por qué DynamoDB para estado de llamadas?
Las llamadas activas son efímeras (minutos-horas). DynamoDB con TTL de 24h es perfecto: baja latencia, pay-per-request, auto-cleanup. Los datos permanentes (transcripción, intención, solicitud) van a PostgreSQL.

---

## Plan de Implementación

### Fase 1: Backend — Webhooks VoIP + Phone Mapping (1–2 días)
- Migración `0005_create_advisor_phones.sql` (mapeo número → asesor)
- Endpoints: `POST /api/calls/voip-webhook`, `POST /api/calls/voip-recording`
- Campos VoIP en entidad `CallRecording`
- Port `PhoneNumberRepository` + adapters
- Unit tests

### Fase 2: Configurar Telnyx + AWS (1 día)
- Cuenta Telnyx + números locales CO/MX
- AWS credentials + `cdk bootstrap`
- `cdk deploy` — crear recursos
- Conectar Voice Connector ↔ Telnyx SIP trunk

### Fase 3: iOS — Chime SDK + CallKit + PushKit (3–5 días)
- `ChimeCallManager.swift` — wrapper Chime SDK
- `CallControlService.swift` — HTTP client al API Gateway
- `CallKitProvider.swift` + `CallKitManager.swift` — llamadas nativas
- `PushKitHandler.swift` — VoIP push
- Bridge KMP ↔ Swift para UI en Compose
- `VoipCallScreen.kt` — pantalla de llamada activa

### Fase 4: Flujo End-to-End (2 días)
- Test outbound: app → PSTN → grabación → transcripción → intención
- Test inbound: PSTN → push → CallKit → app → grabación
- Conectar resultados a UI

### Fase 5: Polish + Edge Cases (2 días)
- Error handling (timeout, desconexión, mic denied)
- Seguridad (API key validation, rate limiting, TLS)
- Monitoring (CloudWatch alarms, métricas)

**Total estimado: 9–12 días**

---

## Costo Estimado por Llamada

| Componente | Costo/min |
|------------|-----------|
| Chime SDK Audio (WebRTC) | $0.0017 |
| Chime SMA (PSTN bridge) | $0.0040 |
| Chime Voice Connector | $0.0022 |
| Telnyx PSTN (Colombia) | $0.0070 |
| Google STT (post-call) | ~$0.0020 |
| **Total** | **~$0.017/min** |

Llamada promedio de 10 min = ~$0.17 USD

---

## Métricas de Éxito

- **Calidad de audio**: MOS score ≥ 3.5 (buena calidad conversacional)
- **Latencia de conexión**: < 3 segundos desde "Llamar" hasta ring en destino
- **Transcripción**: Accuracy ≥ 85% en español colombiano/mexicano
- **Intención detectada**: ≥ 70% de las llamadas con intención real son detectadas
- **Solicitud generada**: < 30 segundos post-hangup para tener solicitud lista

---

## Riesgos y Mitigaciones

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Calidad de audio pobre en redes móviles LatAm | UX degradada | Chime SDK tiene adaptive bitrate; fallback a solo PSTN |
| Telnyx no disponible en región específica | No hay servicio | Backup: Vonage o Bandwidth como SIP trunk alternativo |
| Apple rechaza app por uso de VoIP | No se publica | Documentar uso legítimo de CallKit (requisito de Apple) |
| Costo por minuto alto en volumen | Margen bajo | Negociar volumen con Telnyx; evaluar migrar a FreeSWITCH propio |
| Latencia alta Colombia ↔ us-east-1 | Retraso en audio | Evaluar región sa-east-1 (São Paulo) si disponible en Chime |

---

## Referencias

- [AWS Chime SDK Voice Documentation](https://docs.aws.amazon.com/chime-sdk/latest/dg/voice.html)
- [Telnyx SIP Trunking](https://telnyx.com/products/sip-trunking)
- [Apple CallKit Documentation](https://developer.apple.com/documentation/callkit)
- [Apple PushKit VoIP](https://developer.apple.com/documentation/pushkit)
- `architecture/diagrams/voip-chime-architecture.md` — Diagramas Mermaid detallados
- `infra/lib/infra-stack.ts` — CDK Stack completo

Última actualización: 2026-04-13

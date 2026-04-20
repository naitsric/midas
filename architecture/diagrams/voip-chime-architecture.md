# Arquitectura VoIP — AWS Chime SDK + PSTN

## Diagrama General

```mermaid
graph TB
    subgraph "iOS App (MIDAS)"
        A[Chime SDK iOS<br/>WebRTC + CallKit]
        B[PushKit<br/>VoIP Push]
        C[Contacts<br/>CNContactStore]
    end

    subgraph "AWS Cloud"
        subgraph "API Layer"
            D[API Gateway]
            E[Lambda: Call Control<br/>createMeeting, createAttendee<br/>triggerSMA]
        end

        subgraph "Chime SDK"
            F[Chime Meeting<br/>WebRTC Bridge]
            G[SIP Media Application<br/>PSTN ↔ Meeting Bridge]
            H[Lambda: SMA Handler<br/>call routing, recording<br/>actions]
        end

        subgraph "PSTN Connectivity"
            I[Voice Connector<br/>SIP Trunk Termination]
            J[SIP Rule<br/>Number → SMA Routing]
        end

        subgraph "Storage & Processing"
            K[S3: Grabaciones<br/>WAV stereo]
            L[SNS: VoIP Push<br/>→ APNs PushKit]
        end

        subgraph "MIDAS Backend"
            M[FastAPI<br/>Transcripción + Intent Detection]
            N[PostgreSQL<br/>Calls, Transcripts, Applications]
        end
    end

    subgraph "SIP Trunk Provider (Telnyx)"
        O[Números Locales<br/>+57 Colombia<br/>+52 México]
    end

    subgraph "Cliente (teléfono normal)"
        P[PSTN / Celular]
    end

    C -->|Seleccionar contacto| A
    A -->|HTTPS| D
    D --> E
    E -->|CreateMeeting + CreateAttendee| F
    E -->|CreateSipMediaApplicationCall| G
    A <-->|WebRTC Audio/Video| F
    G <-->|Bridge PSTN ↔ Meeting| F
    G --> H
    H -->|StartCallRecording| K
    H -->|SIP INVITE/BYE| I
    I <-->|SIP Trunk| O
    O <-->|PSTN| P
    J -->|Route Number → SMA| G
    H -->|Push Notification| L
    L -->|APNs VoIP Push| B
    K -->|S3 Event| M
    M --> N

    style A fill:#00E676,color:#000
    style F fill:#FF9800,color:#000
    style G fill:#FF9800,color:#000
    style K fill:#42A5F5,color:#000
    style M fill:#B388FF,color:#000
    style O fill:#EF5350,color:#fff
    style P fill:#9E9E9E,color:#000
```

## Flujo: Llamada Saliente

```mermaid
sequenceDiagram
    participant Asesor as iOS App (Asesor)
    participant API as API Gateway + Lambda
    participant Chime as Chime Meeting
    participant SMA as SIP Media App
    participant Lambda as SMA Lambda
    participant VC as Voice Connector
    participant Telnyx as Telnyx SIP Trunk
    participant Cliente as Cliente (PSTN)
    participant S3 as S3 (Grabación)

    Asesor->>API: POST /dial {toNumber, advisorId}
    API->>Chime: CreateMeeting()
    API->>Chime: CreateAttendee() x2 (asesor + SMA)
    API->>SMA: CreateSipMediaApplicationCall(toNumber)
    API-->>Asesor: MeetingSession config + joinToken

    Asesor->>Chime: Join Meeting (WebRTC)
    Note over Asesor,Chime: Asesor conectado por datos/WiFi

    SMA->>Lambda: NEW_OUTBOUND_CALL
    Lambda-->>SMA: CallAndBridge → toNumber via Voice Connector
    SMA->>VC: SIP INVITE
    VC->>Telnyx: SIP INVITE (+57 311 XXX XXXX)
    Telnyx->>Cliente: Ring ring...
    Cliente-->>Telnyx: Answer
    Telnyx-->>VC: 200 OK
    VC-->>SMA: CALL_ANSWERED

    SMA->>Lambda: CALL_ANSWERED
    Lambda-->>SMA: [JoinChimeMeeting, StartCallRecording]
    SMA->>Chime: Bridge PSTN leg into Meeting
    SMA->>S3: Start recording (WAV stereo)

    Note over Asesor,Cliente: Conversación en curso<br/>Asesor (WebRTC) ↔ Chime ↔ SMA (PSTN) ↔ Cliente

    Cliente-->>SMA: Hangup
    SMA->>Lambda: HANGUP
    Lambda-->>SMA: StopCallRecording
    SMA->>S3: Upload recording.wav
    S3-->>API: S3 Event Notification
    API->>API: Transcribir + Detectar Intención
    API-->>Asesor: Push: "Intención detectada en llamada con Juan"
```

## Flujo: Llamada Entrante

```mermaid
sequenceDiagram
    participant Cliente as Cliente (PSTN)
    participant Telnyx as Telnyx SIP Trunk
    participant VC as Voice Connector
    participant SMA as SIP Media App
    participant Lambda as SMA Lambda
    participant SNS as SNS → APNs
    participant Asesor as iOS App (Asesor)
    participant Chime as Chime Meeting
    participant S3 as S3 (Grabación)

    Cliente->>Telnyx: Marca +57 XXX XXXX
    Telnyx->>VC: SIP INVITE
    VC->>SMA: Route via SIP Rule
    SMA->>Lambda: NEW_INBOUND_CALL

    Lambda->>Chime: CreateMeeting() + CreateAttendee() x2
    Lambda->>SNS: Send VoIP Push {meetingId, joinToken, callerNumber}
    Lambda-->>SMA: Pause(30s) — esperar que el asesor conteste

    SNS->>Asesor: PushKit VoIP notification
    Asesor->>Asesor: CallKit: reportNewIncomingCall()
    Note over Asesor: iPhone suena como llamada normal

    Asesor->>Asesor: Usuario contesta (CallKit)
    Asesor->>Chime: Join Meeting (WebRTC)
    Asesor->>Lambda: UpdateSipMediaApplication("answered")

    Lambda-->>SMA: [JoinChimeMeeting, StartCallRecording]
    SMA->>Chime: Bridge PSTN leg into Meeting
    SMA->>S3: Start recording

    Note over Asesor,Cliente: Conversación en curso

    Asesor-->>Chime: Hangup
    SMA->>Lambda: HANGUP
    SMA->>S3: Upload recording.wav
    S3-->>Lambda: S3 Event → Transcribir + Detectar
```

## Recursos AWS Necesarios (CDK Stack)

| Recurso | Servicio | Propósito |
|---------|----------|-----------|
| SIP Media Application | Chime SDK Voice | Controla flujo de llamadas PSTN |
| SMA Lambda Handler | Lambda | Lógica de routing, recording, bridging |
| SIP Rule | Chime SDK Voice | Asocia números → SMA |
| Voice Connector | Chime SDK Voice | Termina SIP trunk de Telnyx |
| Phone Numbers | Telnyx | Números locales CO/MX (no disponibles nativos en Chime) |
| Call Control Lambda | Lambda | API para iOS: /dial, /answer, /hangup |
| API Gateway | API Gateway | Expone endpoints HTTPS para iOS |
| S3 Bucket | S3 | Almacena grabaciones WAV |
| SNS Topic | SNS | Envia VoIP push a APNs para llamadas entrantes |
| DynamoDB Table | DynamoDB | Estado de llamadas activas (meeting → advisor mapping) |

## Notas Importantes

1. **Colombia/México**: Chime no ofrece números nativos. Se usa Telnyx como SIP trunk provider con números locales (+57, +52) conectados via Voice Connector.
2. **Grabación**: Server-side en Chime (StartCallRecording action). Audio se almacena en S3 como WAV stereo (canal izquierdo = entrante, derecho = saliente).
3. **iOS SDK**: `amazon-chime-sdk-ios` via SPM. WebRTC-based, soporta CallKit nativo.
4. **PushKit**: Obligatorio para llamadas entrantes. APNs VoIP push despierta la app incluso cerrada.
5. **Costo estimado**: ~$0.008/min (Chime audio) + ~$0.007/min (Telnyx PSTN Colombia) = ~$0.015/min total.

Última actualización: 2026-04-13

import asyncio
import json
import time
from uuid import UUID

from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect

from src.advisor.application.use_cases import AuthenticateAdvisor
from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import AdvisorAuthenticationError
from src.advisor.domain.ports import AdvisorRepository
from src.advisor.domain.value_objects import ApiKey
from src.advisor.infrastructure.auth import RequireAdvisor
from src.application.application.use_cases import GenerateCreditApplication
from src.application.domain.exceptions import ApplicationGenerationError
from src.application.domain.ports import ApplicationGenerator, ApplicationRepository
from src.application.infrastructure.schemas import (
    ApplicantResponse,
    CreditApplicationResponse,
    ProductRequestResponse,
)
from src.call.application.use_cases import EndCall, ListCalls, StartCall
from src.call.domain.entities import CallRecording
from src.call.domain.ports import CallRepository, PhoneNumberRepository, SpeechTranscriber
from src.call.domain.value_objects import CallId, CallStatus
from src.call.infrastructure.schemas import (
    CallResponse,
    CallSummaryResponse,
    StartCallRequest,
    VoipRecordingRequest,
    VoipWebhookRequest,
)
from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import ConversationId, MessageSender
from src.intent.application.use_cases import DetectFinancialIntent
from src.intent.domain.ports import IntentDetector


async def _process_voip_recording(
    call_id: CallId,
    recording_url: str,
    content_type: str,
    duration_seconds: int,
    repository: CallRepository,
    advisor_repo: AdvisorRepository | None,
    intent_detector: IntentDetector | None,
    application_repo: ApplicationRepository | None,
    application_generator: ApplicationGenerator | None,
) -> None:
    """
    Background task: download recorded audio, transcribe with Gemini,
    detect financial intent, and optionally generate a credit application.
    """
    import logging

    import httpx

    logger = logging.getLogger("midas.call.voip-recording")

    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.get(recording_url)
            resp.raise_for_status()
            audio_bytes = resp.content

        logger.info(f"Downloaded {len(audio_bytes)} bytes of audio for call {call_id}")

        # S3 often serves recorded files as application/octet-stream; Gemini
        # rejects that. Default to audio/wav (the Chime SMA recording format).
        if not content_type or content_type == "application/octet-stream":
            content_type = "audio/wav"

        # Transcribe via Gemini (file mode — single request, not streaming)
        import os

        import google.generativeai as genai

        gemini_key = os.getenv("GEMINI_API_KEY")
        if not gemini_key:
            logger.warning("GEMINI_API_KEY not set; skipping transcription")
            return

        genai.configure(api_key=gemini_key)
        prompt = (
            "Transcribe esta llamada telefónica completa. "
            "Devuelve solo la transcripción, identificando los hablantes "
            "como 'Asesor:' y 'Cliente:' al inicio de cada turno. "
            "Si no es claro, deja como 'Hablante:'."
        )
        # 2.5-flash is the current default; 2.0-flash kept as fallback only.
        transcript = ""
        last_error: Exception | None = None
        for model_name in ("gemini-2.5-flash", "gemini-2.0-flash"):
            try:
                model = genai.GenerativeModel(model_name)
                response = await model.generate_content_async(
                    [{"mime_type": content_type, "data": audio_bytes}, prompt]
                )
                transcript = (response.text or "").strip()
                last_error = None
                break
            except Exception as e:
                last_error = e
                msg = str(e)
                if "RESOURCE_EXHAUSTED" in msg or "429" in msg:
                    logger.warning(f"{model_name} quota exhausted, trying next model")
                    continue
                raise
        if last_error is not None:
            raise last_error

        # Re-fetch the call (it may have been updated meanwhile)
        call = await repository.find_by_id(call_id)
        if call is None:
            logger.warning(f"Call {call_id} disappeared during processing")
            return

        if transcript:
            # Bypass the state-machine guard since we may already be PROCESSING
            call.transcript = transcript
        if call.status == CallStatus.PROCESSING:
            call.complete(duration_seconds=duration_seconds)
        await repository.save(call)
        logger.info(f"Transcript saved for {call_id} ({len(transcript)} chars)")

        # Detect intent + optionally generate application
        if not transcript or intent_detector is None or advisor_repo is None:
            return

        advisor = await advisor_repo.find_by_id(call.advisor_id)
        if advisor is None:
            logger.warning(f"Advisor {call.advisor_id} not found")
            return

        conv = Conversation(
            id=ConversationId.generate(),
            advisor_id=advisor.id,
            advisor_name=advisor.name,
            client_name=call.client_name,
        )
        conv.add_message(MessageSender(name=call.client_name, is_advisor=False), transcript)

        detect = DetectFinancialIntent(intent_detector)
        intent = await detect.execute(conv)
        logger.info(
            f"Intent for {call_id}: detected={intent.intent_detected} "
            f"actionable={intent.is_actionable} product={intent.product_type}"
        )

        if (
            intent.is_actionable
            and application_repo is not None
            and application_generator is not None
        ):
            try:
                generate = GenerateCreditApplication(
                    repository=application_repo, generator=application_generator
                )
                app = await generate.execute(
                    advisor_id=advisor.id, conversation=conv, intent=intent
                )
                logger.info(f"Generated credit application {app.id} for call {call_id}")
            except ApplicationGenerationError as e:
                logger.warning(f"Could not auto-generate application for {call_id}: {e}")

    except Exception:
        logger.exception(f"Failed to process VoIP recording for call {call_id}")
        # Don't leave the call stuck in PROCESSING — mark it failed so the UI
        # surfaces the state and the advisor can retry manually.
        try:
            call = await repository.find_by_id(call_id)
            if call is not None and call.status == CallStatus.PROCESSING:
                call.fail()
                await repository.save(call)
        except Exception:
            logger.exception(f"Also failed to mark {call_id} as FAILED")


def create_call_router(
    repository: CallRepository,
    transcriber: SpeechTranscriber | None = None,
    authenticate: AuthenticateAdvisor | None = None,
    intent_detector: IntentDetector | None = None,
    application_repo: ApplicationRepository | None = None,
    application_generator: ApplicationGenerator | None = None,
    phone_repo: PhoneNumberRepository | None = None,
    advisor_repo: AdvisorRepository | None = None,
) -> APIRouter:
    router = APIRouter(prefix="/api/calls", tags=["calls"])
    start_call = StartCall(repository)
    end_call = EndCall(repository)
    list_calls = ListCalls(repository)
    # Hold strong references to background tasks so the asyncio event loop
    # doesn't GC them mid-flight (Python 3.12 enforces this strictly).
    background_tasks: set[asyncio.Task[None]] = set()

    detect_intent = DetectFinancialIntent(intent_detector) if intent_detector else None
    generate_application = (
        GenerateCreditApplication(repository=application_repo, generator=application_generator)
        if application_repo and application_generator
        else None
    )

    def _to_response(call: CallRecording) -> CallResponse:
        # Generate a fresh pre-signed URL on every read — the URL stored in
        # `call.recording_url` came from the Recording Processor Lambda with
        # a 1h TTL and is likely expired by the time the advisor opens the
        # detail screen. Falls back to the stored URL if signing isn't
        # configured (tests, dev without AWS creds).
        from src.call.infrastructure.s3_signing import presign_recording_url

        fresh_url = presign_recording_url(call.recording_key) or call.recording_url
        return CallResponse(
            id=str(call.id),
            client_name=call.client_name,
            status=call.status.value,
            transcript=call.transcript,
            duration_seconds=call.duration_seconds,
            voip_call_id=call.voip_call_id,
            recording_url=fresh_url,
            created_at=call.created_at.isoformat(),
            completed_at=call.completed_at.isoformat() if call.completed_at else None,
        )

    def _to_summary(call: CallRecording) -> CallSummaryResponse:
        return CallSummaryResponse(
            id=str(call.id),
            client_name=call.client_name,
            status=call.status.value,
            duration_seconds=call.duration_seconds,
            created_at=call.created_at.isoformat(),
        )

    def _call_to_conversation(call: CallRecording, advisor: Advisor) -> Conversation:
        """Convierte un CallRecording con transcript a una Conversation para intent detection."""
        conv = Conversation(
            id=ConversationId.generate(),
            advisor_id=advisor.id,
            advisor_name=advisor.name,
            client_name=call.client_name,
        )
        # Poner todo el transcript como un mensaje del cliente
        # (el intent detector analiza el contenido completo)
        if call.transcript and call.transcript.strip():
            conv.add_message(MessageSender(name=call.client_name, is_advisor=False), call.transcript)
        return conv

    async def _authenticate_ws(api_key: str | None) -> Advisor | None:
        """Autentica via query param para WebSocket."""
        if authenticate is None or api_key is None:
            return None
        try:
            return await authenticate.execute(ApiKey(api_key))
        except AdvisorAuthenticationError:
            return None

    @router.post("", status_code=201)
    async def create_call(request: StartCallRequest, advisor: Advisor = RequireAdvisor) -> CallResponse:
        call = await start_call.execute(
            advisor_id=advisor.id,
            client_name=request.client_name,
            voip_call_id=request.voip_call_id,
        )
        return _to_response(call)

    @router.get("")
    async def list_advisor_calls(advisor: Advisor = RequireAdvisor) -> list[CallSummaryResponse]:
        calls = await list_calls.execute(advisor.id)
        return [_to_summary(c) for c in calls]

    @router.get("/{call_id}")
    async def get_call(call_id: UUID, advisor: Advisor = RequireAdvisor) -> CallResponse:
        call = await repository.find_by_id_and_advisor(CallId(call_id), advisor.id)
        if call is None:
            raise HTTPException(status_code=404, detail="Llamada no encontrada")
        return _to_response(call)

    @router.post("/{call_id}/end")
    async def end_recording(call_id: UUID, advisor: Advisor = RequireAdvisor) -> CallResponse:
        call = await repository.find_by_id_and_advisor(CallId(call_id), advisor.id)
        if call is None:
            raise HTTPException(status_code=404, detail="Llamada no encontrada")
        call = await end_call.execute(call)
        return _to_response(call)

    @router.post("/{call_id}/detect-intent")
    async def detect_call_intent(call_id: UUID, advisor: Advisor = RequireAdvisor):
        """Detecta intención financiera en el transcript de una llamada."""
        if detect_intent is None:
            raise HTTPException(status_code=501, detail="Intent detector no configurado")

        call = await repository.find_by_id_and_advisor(CallId(call_id), advisor.id)
        if call is None:
            raise HTTPException(status_code=404, detail="Llamada no encontrada")

        if not call.transcript or not call.transcript.strip():
            raise HTTPException(status_code=422, detail="La llamada no tiene transcripción")

        conv = _call_to_conversation(call, advisor)
        result = await detect_intent.execute(conv)

        return {
            "intent_detected": result.intent_detected,
            "confidence": result.confidence.value,
            "product_type": result.product_type.value if result.product_type else None,
            "entities": {
                "amount": result.entities.amount,
                "term": result.entities.term,
                "location": result.entities.location,
                **result.entities.additional,
            },
            "summary": result.summary,
            "is_actionable": result.is_actionable,
        }

    @router.post("/{call_id}/generate-application", status_code=201)
    async def generate_app_from_call(call_id: UUID, advisor: Advisor = RequireAdvisor):
        """Genera solicitud de crédito desde el transcript de una llamada."""
        if detect_intent is None:
            raise HTTPException(status_code=501, detail="Intent detector no configurado")
        if generate_application is None:
            raise HTTPException(status_code=501, detail="Application generator no configurado")

        call = await repository.find_by_id_and_advisor(CallId(call_id), advisor.id)
        if call is None:
            raise HTTPException(status_code=404, detail="Llamada no encontrada")

        if not call.transcript or not call.transcript.strip():
            raise HTTPException(status_code=422, detail="La llamada no tiene transcripción")

        conv = _call_to_conversation(call, advisor)

        try:
            intent = await detect_intent.execute(conv)
        except Exception as e:
            raise HTTPException(status_code=422, detail=f"Error detectando intención: {e}") from e

        try:
            app = await generate_application.execute(advisor_id=advisor.id, conversation=conv, intent=intent)
        except ApplicationGenerationError as e:
            raise HTTPException(status_code=422, detail=str(e)) from e

        return CreditApplicationResponse(
            id=str(app.id),
            status=app.status.value,
            status_label=app.status.label,
            applicant=ApplicantResponse(
                full_name=app.applicant.full_name,
                phone=app.applicant.phone,
                estimated_income=app.applicant.estimated_income,
                employment_type=app.applicant.employment_type,
                completeness=app.applicant.completeness,
            ),
            product_request=ProductRequestResponse(
                product_type=app.product_request.product_type.value,
                product_label=app.product_request.product_type.label,
                amount=app.product_request.amount,
                term=app.product_request.term,
                location=app.product_request.location,
                summary=app.product_request.summary,
            ),
            conversation_summary=app.conversation_summary,
            rejection_reason=app.rejection_reason,
            created_at=app.created_at.isoformat(),
        )

    # ─── VoIP Webhooks (llamados por Lambda handlers, sin auth de asesor) ───

    @router.post("/voip-webhook")
    async def voip_webhook(request: VoipWebhookRequest):
        """Recibe eventos del SMA Handler Lambda (call_ended, etc.)."""
        if request.event == "call_ended":
            call = await repository.find_by_voip_call_id(request.call_id)
            if call and call.status == CallStatus.RECORDING:
                call.mark_processing()
                await repository.save(call)
        return {"status": "ok"}

    @router.post("/voip-recording")
    async def voip_recording(request: VoipRecordingRequest):
        """
        Recibe metadata de grabación del Recording Processor Lambda y dispara
        el pipeline asíncrono: descarga audio → transcribe → detecta intención
        → genera credit application si es actionable.
        """
        call = await repository.find_by_voip_call_id(request.call_id)
        if call is None:
            raise HTTPException(status_code=404, detail="Llamada no encontrada")

        call.recording_url = request.recording_url
        call.recording_key = request.key
        call.duration_seconds = request.duration_seconds

        if call.status == CallStatus.RECORDING:
            call.mark_processing()
        await repository.save(call)

        # Run transcription + intent detection in the background so the Lambda
        # webhook returns immediately (audio may take 10–30s to process).
        task = asyncio.create_task(
            _process_voip_recording(
                call_id=call.id,
                recording_url=request.recording_url,
                content_type=request.content_type,
                duration_seconds=request.duration_seconds,
                repository=repository,
                advisor_repo=advisor_repo,
                intent_detector=intent_detector,
                application_repo=application_repo,
                application_generator=application_generator,
            )
        )
        background_tasks.add(task)
        task.add_done_callback(background_tasks.discard)

        return {"status": "ok", "call_id": str(call.id)}

    @router.get("/advisor-by-phone/{phone}")
    async def get_advisor_by_phone(phone: str):
        """Lookup interno: qué asesor tiene asignado este número telefónico."""
        if phone_repo is None:
            raise HTTPException(status_code=501, detail="Phone repository no configurado")
        advisor_id = await phone_repo.find_advisor_by_phone(phone)
        if advisor_id is None:
            raise HTTPException(status_code=404, detail="Número no asignado a ningún asesor")
        return {"advisor_id": str(advisor_id), "phone_number": phone}

    @router.websocket("/{call_id}/stream")
    async def stream_audio(websocket: WebSocket, call_id: UUID, api_key: str | None = None):
        """
        WebSocket para streaming de audio en tiempo real.

        Protocolo:
        - Cliente envía: binary frames (audio PCM 16kHz 16-bit mono)
        - Cliente envía: text frame {"action": "end"} para finalizar
        - Servidor envía: text frames {"type": "transcript", "text": "...", "is_final": bool}
        - Servidor envía: text frame {"type": "intent", ...} con resultado de detección
        - Servidor envía: text frame {"type": "completed", "call_id": "..."}
        - Servidor envía: text frame {"type": "error", "message": "..."}
        """
        # Autenticar via query param
        advisor = await _authenticate_ws(api_key)
        if advisor is None:
            await websocket.close(code=4001, reason="API key inválida")
            return

        # Verificar que la llamada existe y pertenece al asesor
        call = await repository.find_by_id_and_advisor(CallId(call_id), advisor.id)
        if call is None:
            await websocket.close(code=4004, reason="Llamada no encontrada")
            return

        if call.status != CallStatus.RECORDING:
            await websocket.close(code=4009, reason="La llamada no está en estado de grabación")
            return

        await websocket.accept()
        start_time = time.time()

        if transcriber is not None:
            audio_queue: asyncio.Queue[bytes | None] = asyncio.Queue()

            async def audio_generator():
                while True:
                    chunk = await audio_queue.get()
                    if chunk is None:
                        break
                    yield chunk

            async def receive_audio():
                try:
                    while True:
                        message = await websocket.receive()
                        if message.get("type") == "websocket.disconnect":
                            break
                        if "bytes" in message:
                            await audio_queue.put(message["bytes"])
                        elif "text" in message:
                            data = json.loads(message["text"])
                            if data.get("action") == "end":
                                break
                except WebSocketDisconnect:
                    pass
                finally:
                    await audio_queue.put(None)

            async def process_transcription():
                try:
                    async for chunk in transcriber.transcribe_stream(audio_generator()):
                        if chunk.is_final:
                            call.append_transcript(chunk.text)
                            await repository.save(call)
                        try:
                            await websocket.send_json(
                                {
                                    "type": "transcript",
                                    "text": chunk.text,
                                    "is_final": chunk.is_final,
                                }
                            )
                        except RuntimeError:
                            break  # WebSocket closed
                except Exception as e:
                    try:
                        await websocket.send_json({"type": "error", "message": str(e)})
                    except RuntimeError:
                        pass  # WebSocket already closed

            receive_task = asyncio.create_task(receive_audio())
            transcribe_task = asyncio.create_task(process_transcription())
            done, pending = await asyncio.wait(
                [receive_task, transcribe_task],
                return_when=asyncio.FIRST_COMPLETED,
            )
            for task in pending:
                task.cancel()
            for task in done:
                if task.exception():
                    pass  # Already handled
        else:
            # Sin transcriber: solo recibir audio hasta "end", sin transcribir
            try:
                while True:
                    message = await websocket.receive()
                    if message.get("type") == "websocket.disconnect":
                        break
                    if "text" in message:
                        data = json.loads(message["text"])
                        if data.get("action") == "end":
                            break
            except WebSocketDisconnect:
                pass

        # Finalizar la llamada
        duration = int(time.time() - start_time)
        call.mark_processing()
        call.complete(duration_seconds=duration)
        await repository.save(call)

        # Detectar intención si hay transcript y detector configurado
        import logging
        logger = logging.getLogger("midas.call")
        intent_result = None
        if detect_intent and call.transcript and call.transcript.strip():
            try:
                logger.info(f"Detectando intención para llamada {call.id}...")
                conv = _call_to_conversation(call, advisor)
                result = await detect_intent.execute(conv)
                intent_result = {
                    "type": "intent",
                    "intent_detected": result.intent_detected,
                    "confidence": result.confidence.value,
                    "product_type": result.product_type.value if result.product_type else None,
                    "summary": result.summary,
                    "is_actionable": result.is_actionable,
                    "call_id": str(call.id),
                }
                await websocket.send_json(intent_result)
            except Exception:
                pass

        try:
            await websocket.send_json(
                {
                    "type": "completed",
                    "call_id": str(call.id),
                    "duration_seconds": duration,
                    "transcript": call.transcript,
                }
            )
        except Exception:
            pass

        try:
            await websocket.close()
        except Exception:
            pass

    return router

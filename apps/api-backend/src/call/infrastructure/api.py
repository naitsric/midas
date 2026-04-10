import asyncio
import json
import time
from uuid import UUID

from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect

from src.advisor.application.use_cases import AuthenticateAdvisor
from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import AdvisorAuthenticationError
from src.advisor.domain.value_objects import ApiKey
from src.advisor.infrastructure.auth import RequireAdvisor
from src.call.application.use_cases import EndCall, ListCalls, StartCall
from src.call.domain.entities import CallRecording
from src.call.domain.ports import CallRepository, SpeechTranscriber
from src.call.domain.value_objects import CallId, CallStatus
from src.call.infrastructure.schemas import CallResponse, CallSummaryResponse, StartCallRequest


def create_call_router(
    repository: CallRepository,
    transcriber: SpeechTranscriber | None = None,
    authenticate: AuthenticateAdvisor | None = None,
) -> APIRouter:
    router = APIRouter(prefix="/api/calls", tags=["calls"])
    start_call = StartCall(repository)
    end_call = EndCall(repository)
    list_calls = ListCalls(repository)

    def _to_response(call: CallRecording) -> CallResponse:
        return CallResponse(
            id=str(call.id),
            client_name=call.client_name,
            status=call.status.value,
            transcript=call.transcript,
            duration_seconds=call.duration_seconds,
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
        call = await start_call.execute(advisor_id=advisor.id, client_name=request.client_name)
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

    @router.websocket("/{call_id}/stream")
    async def stream_audio(websocket: WebSocket, call_id: UUID, api_key: str | None = None):
        """
        WebSocket para streaming de audio en tiempo real.

        Protocolo:
        - Cliente envía: binary frames (audio PCM 16kHz 16-bit mono)
        - Cliente envía: text frame {"action": "end"} para finalizar
        - Servidor envía: text frames {"type": "transcript", "text": "...", "is_final": bool}
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
                        await websocket.send_json(
                            {
                                "type": "transcript",
                                "text": chunk.text,
                                "is_final": chunk.is_final,
                            }
                        )
                except Exception as e:
                    await websocket.send_json({"type": "error", "message": str(e)})

            receive_task = asyncio.create_task(receive_audio())
            transcribe_task = asyncio.create_task(process_transcription())
            await receive_task
            await transcribe_task
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

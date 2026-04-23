"""
FastAPI router del Copilot — un solo endpoint POST con SSE streaming.

Cada request:
1. Auth via X-API-Key (RequireAdvisor) → resuelve `Advisor` del tenant.
2. Construye historial desde el body (DTO → CopilotMessage VOs).
3. Ejecuta `RunCopilotTurn` y serializa cada CopilotEvent a SSE.

El cliente recibe un text/event-stream con eventos JSON-lines:
  event: token       data: {"text": "..."}
  event: tool_call   data: {"name": "..."}
  event: tool_result data: {"name": "...", "source": {"type":"...","label":"..."}}
  event: done        data: {"elapsed_ms": 1234}
  event: error       data: {"message": "..."}
"""

import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from src.advisor.domain.entities import Advisor
from src.advisor.infrastructure.auth import RequireAdvisor
from src.copilot.application.use_cases import RunCopilotTurn
from src.copilot.domain.events import (
    CopilotEvent,
    DoneEvent,
    ErrorEvent,
    TokenEvent,
    ToolCallEvent,
    ToolResultEvent,
)
from src.copilot.domain.ports import CopilotAgent
from src.copilot.domain.value_objects import CopilotMessage, MessageRole
from src.copilot.infrastructure.schemas import CopilotMessageRequest


def _format_sse(event_name: str, data: dict) -> bytes:
    """Frame un evento SSE: 'event: X\\n' + 'data: <json>\\n\\n'."""
    payload = json.dumps(data, ensure_ascii=False)
    return f"event: {event_name}\ndata: {payload}\n\n".encode()


def _serialize(event: CopilotEvent) -> bytes:
    if isinstance(event, TokenEvent):
        return _format_sse("token", {"text": event.text})
    if isinstance(event, ToolCallEvent):
        return _format_sse("tool_call", {"name": event.name})
    if isinstance(event, ToolResultEvent):
        data: dict = {"name": event.name}
        if event.source is not None:
            data["source"] = {"type": event.source.type.value, "label": event.source.label}
        return _format_sse("tool_result", data)
    if isinstance(event, DoneEvent):
        return _format_sse("done", {"elapsed_ms": event.elapsed_ms})
    if isinstance(event, ErrorEvent):
        return _format_sse("error", {"message": event.message})
    raise ValueError(f"Evento de Copilot desconocido: {event!r}")


def _history_dto_to_vo(items: list) -> list[CopilotMessage]:
    out: list[CopilotMessage] = []
    for item in items:
        try:
            role = MessageRole(item.role)
        except ValueError:
            continue  # ignoramos roles desconocidos en lugar de fallar
        if not item.text or not item.text.strip():
            continue
        out.append(CopilotMessage(role=role, text=item.text))
    return out


def create_copilot_router(agent: CopilotAgent) -> APIRouter:
    router = APIRouter(prefix="/api/copilot", tags=["copilot"])
    use_case = RunCopilotTurn(agent)

    @router.post("/messages")
    async def post_message(
        request: CopilotMessageRequest,
        advisor: Advisor = RequireAdvisor,
    ) -> StreamingResponse:
        history = _history_dto_to_vo(request.history)

        async def stream():
            async for event in use_case.execute(advisor, history, request.message):
                yield _serialize(event)

        return StreamingResponse(
            stream(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
                "Connection": "keep-alive",
            },
        )

    return router

"""Tests para el serializer SSE del copilot router."""

import json

from src.copilot.domain.events import (
    ClientActionEvent,
    DoneEvent,
    ErrorEvent,
    TokenEvent,
    ToolCallEvent,
    ToolResultEvent,
)
from src.copilot.domain.value_objects import SourceRef, SourceType
from src.copilot.infrastructure.api import _serialize


def _parse_sse(raw: bytes) -> tuple[str, dict]:
    """Extrae (event_name, data_dict) de un frame SSE."""
    text = raw.decode()
    lines = text.strip().split("\n")
    event_name = ""
    data_payload = ""
    for line in lines:
        if line.startswith("event: "):
            event_name = line[len("event: "):]
        elif line.startswith("data: "):
            data_payload = line[len("data: "):]
    return event_name, json.loads(data_payload)


class TestSerialize:
    def test_token(self):
        name, data = _parse_sse(_serialize(TokenEvent(text="hola")))
        assert name == "token"
        assert data == {"text": "hola"}

    def test_tool_call(self):
        name, data = _parse_sse(_serialize(ToolCallEvent(name="list_recent_calls")))
        assert name == "tool_call"
        assert data == {"name": "list_recent_calls"}

    def test_tool_result_with_source(self):
        ev = ToolResultEvent(
            name="get_call_transcript",
            source=SourceRef(type=SourceType.CALL, label="Llamada · Juan"),
        )
        name, data = _parse_sse(_serialize(ev))
        assert name == "tool_result"
        assert data == {"name": "get_call_transcript", "source": {"type": "call", "label": "Llamada · Juan"}}

    def test_tool_result_without_source(self):
        name, data = _parse_sse(_serialize(ToolResultEvent(name="x", source=None)))
        assert name == "tool_result"
        assert "source" not in data

    def test_client_action(self):
        ev = ClientActionEvent(
            action="create_event",
            payload={"title": "Call Juan", "when_iso": "2026-04-24T10:00:00-05:00", "duration_minutes": 30},
        )
        name, data = _parse_sse(_serialize(ev))
        assert name == "client_action"
        assert data["action"] == "create_event"
        assert data["payload"]["title"] == "Call Juan"
        assert data["payload"]["duration_minutes"] == 30

    def test_done(self):
        name, data = _parse_sse(_serialize(DoneEvent(elapsed_ms=2400)))
        assert name == "done"
        assert data == {"elapsed_ms": 2400}

    def test_error(self):
        name, data = _parse_sse(_serialize(ErrorEvent(message="boom")))
        assert name == "error"
        assert data == {"message": "boom"}

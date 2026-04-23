from collections.abc import AsyncIterator
from uuid import uuid4

import pytest

from src.advisor.domain.entities import Advisor
from src.advisor.domain.value_objects import AdvisorId, AdvisorStatus, ApiKey
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
from src.copilot.domain.value_objects import CopilotMessage, MessageRole, SourceRef, SourceType


def _advisor() -> Advisor:
    return Advisor(
        id=AdvisorId(uuid4()),
        name="Test Asesor",
        email="t@t.com",
        phone="+57300000000",
        status=AdvisorStatus.ACTIVE,
        api_key=ApiKey("midas_test_key_12345678901234"),
    )


class FakeAgent(CopilotAgent):
    def __init__(self, events: list[CopilotEvent]):
        self._events = events
        self.calls: list[tuple[str, list[CopilotMessage]]] = []

    async def run(  # type: ignore[override]
        self, advisor, history, message
    ) -> AsyncIterator[CopilotEvent]:
        self.calls.append((message, history))
        for ev in self._events:
            yield ev


class FailingAgent(CopilotAgent):
    async def run(  # type: ignore[override]
        self, advisor, history, message
    ) -> AsyncIterator[CopilotEvent]:
        raise RuntimeError("boom")
        yield  # pragma: no cover


@pytest.mark.asyncio
async def test_emits_agent_events_then_done():
    agent = FakeAgent(
        events=[
            ToolCallEvent(name="list_recent_calls"),
            ToolResultEvent(
                name="list_recent_calls",
                source=SourceRef(type=SourceType.CALL, label="Llamada · Juan"),
            ),
            TokenEvent(text="Tenés 1 llamada"),
        ]
    )
    use_case = RunCopilotTurn(agent)
    advisor = _advisor()

    events = [e async for e in use_case.execute(advisor, [], "cuántas llamadas?")]

    assert len(events) == 4
    assert isinstance(events[0], ToolCallEvent)
    assert isinstance(events[1], ToolResultEvent)
    assert isinstance(events[2], TokenEvent)
    assert isinstance(events[3], DoneEvent)
    assert events[3].elapsed_ms >= 0


@pytest.mark.asyncio
async def test_passes_history_to_agent():
    agent = FakeAgent(events=[TokenEvent(text="ok")])
    use_case = RunCopilotTurn(agent)
    history = [CopilotMessage(role=MessageRole.USER, text="anterior")]

    [_ async for _ in use_case.execute(_advisor(), history, "nuevo")]

    assert agent.calls == [("nuevo", history)]


@pytest.mark.asyncio
async def test_rejects_empty_message():
    agent = FakeAgent(events=[])
    use_case = RunCopilotTurn(agent)

    events = [e async for e in use_case.execute(_advisor(), [], "   ")]

    assert len(events) == 1
    assert isinstance(events[0], ErrorEvent)
    assert "vacío" in events[0].message


@pytest.mark.asyncio
async def test_wraps_agent_exception_as_error_event():
    use_case = RunCopilotTurn(FailingAgent())

    events = [e async for e in use_case.execute(_advisor(), [], "hola")]

    assert len(events) == 1
    assert isinstance(events[0], ErrorEvent)
    assert "boom" in events[0].message


@pytest.mark.asyncio
async def test_strips_message_whitespace_before_passing_to_agent():
    agent = FakeAgent(events=[])
    use_case = RunCopilotTurn(agent)

    [_ async for _ in use_case.execute(_advisor(), [], "  hola  ")]

    assert agent.calls[0][0] == "hola"

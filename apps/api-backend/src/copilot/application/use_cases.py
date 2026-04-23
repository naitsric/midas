"""
RunCopilotTurn — orquesta un turno de chat:
1. Recibe historial + mensaje nuevo del asesor autenticado.
2. Delega al CopilotAgent (ADK adapter en producción).
3. Cierra el stream con un DoneEvent (o ErrorEvent si algo falla).

Mantenemos esto delgado: la lógica de tools / streaming vive en el adapter.
"""

import time
from collections.abc import AsyncIterator

from src.advisor.domain.entities import Advisor
from src.copilot.domain.events import CopilotEvent, DoneEvent, ErrorEvent
from src.copilot.domain.ports import CopilotAgent
from src.copilot.domain.value_objects import CopilotMessage


class RunCopilotTurn:
    def __init__(self, agent: CopilotAgent):
        self._agent = agent

    async def execute(
        self,
        advisor: Advisor,
        history: list[CopilotMessage],
        message: str,
    ) -> AsyncIterator[CopilotEvent]:
        if not message or not message.strip():
            yield ErrorEvent(message="El mensaje no puede estar vacío")
            return

        start = time.monotonic()
        try:
            async for event in self._agent.run(advisor, history, message.strip()):
                yield event
        except Exception as e:  # noqa: BLE001 — el agente envuelve sus propios errores
            yield ErrorEvent(message=f"Error del asistente: {e}")
            return

        elapsed_ms = int((time.monotonic() - start) * 1000)
        yield DoneEvent(elapsed_ms=elapsed_ms)

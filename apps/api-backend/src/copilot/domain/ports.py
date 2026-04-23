"""
Port abstracto para el agente Copilot.

El use case `RunCopilotTurn` depende de esta interfaz, no de ADK directo —
así los tests usan fakes y el adapter real (`infrastructure/adk/agent.py`)
es intercambiable.
"""

from abc import ABC, abstractmethod
from collections.abc import AsyncIterator

from src.advisor.domain.entities import Advisor
from src.copilot.domain.events import CopilotEvent
from src.copilot.domain.value_objects import CopilotMessage


class CopilotAgent(ABC):
    @abstractmethod
    def run(
        self,
        advisor: Advisor,
        history: list[CopilotMessage],
        message: str,
    ) -> AsyncIterator[CopilotEvent]:
        """Ejecuta un turno del agente. Yields CopilotEvent en orden."""
        ...

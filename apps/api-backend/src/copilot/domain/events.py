"""
Eventos que el use case `RunCopilotTurn` emite hacia el endpoint SSE.

Son value objects inmutables — el endpoint los serializa a JSON y los
escribe como `event: <type>` + `data: <json>` líneas en el stream.
"""

from dataclasses import dataclass

from src.copilot.domain.value_objects import SourceRef


@dataclass(frozen=True)
class TokenEvent:
    text: str  # fragmento de texto del modelo (puede ser parcial o completo)


@dataclass(frozen=True)
class ToolCallEvent:
    name: str  # ej. "list_recent_calls"


@dataclass(frozen=True)
class ToolResultEvent:
    name: str
    source: SourceRef | None  # None si el resultado no produce un chip de UI


@dataclass(frozen=True)
class DoneEvent:
    elapsed_ms: int


@dataclass(frozen=True)
class ErrorEvent:
    message: str


@dataclass(frozen=True)
class ClientActionEvent:
    """Instrucción para que el cliente (app móvil) ejecute una acción nativa.

    El backend no ejecuta la acción — solo la propone. La app iOS recibe
    este evento en el stream SSE y dispara el handler correspondiente
    (ej. EventKit para `create_event`).
    """

    action: str  # "create_event", "open_url", etc.
    payload: dict  # JSON-serializable; schema depende del action


CopilotEvent = (
    TokenEvent
    | ToolCallEvent
    | ToolResultEvent
    | ClientActionEvent
    | DoneEvent
    | ErrorEvent
)

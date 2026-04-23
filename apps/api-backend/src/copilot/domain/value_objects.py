from dataclasses import dataclass
from enum import Enum


class MessageRole(str, Enum):
    USER = "user"
    ASSISTANT = "assistant"


class SourceType(str, Enum):
    """Tipos de fuente que la UI mobile renderiza como chips bajo cada respuesta."""

    CALL = "call"
    CHAT = "chat"
    APPLICATION = "application"


@dataclass(frozen=True)
class CopilotMessage:
    role: MessageRole
    text: str

    def __post_init__(self) -> None:
        if not self.text or not self.text.strip():
            raise ValueError("El texto del mensaje no puede estar vacío")


@dataclass(frozen=True)
class SourceRef:
    """Referencia a un dato consultado por el agente, mostrado en la UI como chip."""

    type: SourceType
    label: str  # ej. "Llamada · Juan Pérez", "WhatsApp · 8 msgs", "Solicitud #1204"


@dataclass(frozen=True)
class ToolInvocation:
    name: str
    args: dict

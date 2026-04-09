from dataclasses import dataclass
from uuid import UUID, uuid4


@dataclass(frozen=True)
class ConversationId:
    value: UUID

    @classmethod
    def generate(cls) -> "ConversationId":
        return cls(value=uuid4())

    def __str__(self) -> str:
        return str(self.value)


@dataclass(frozen=True)
class MessageSender:
    name: str
    is_advisor: bool

    def __post_init__(self):
        if not self.name or not self.name.strip():
            raise ValueError("El nombre del sender no puede estar vacío")

    @classmethod
    def advisor(cls, name: str) -> "MessageSender":
        return cls(name=name, is_advisor=True)

    @classmethod
    def client(cls, name: str) -> "MessageSender":
        return cls(name=name, is_advisor=False)

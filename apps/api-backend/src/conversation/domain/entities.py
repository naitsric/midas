from dataclasses import dataclass, field
from datetime import UTC, datetime

from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.exceptions import EmptyConversationError, InvalidMessageError
from src.conversation.domain.value_objects import ConversationId, MessageSender


@dataclass(frozen=True)
class Message:
    sender: MessageSender
    text: str
    timestamp: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self):
        if not self.text or not self.text.strip():
            raise InvalidMessageError("El texto del mensaje no puede estar vacío")


@dataclass
class Conversation:
    id: ConversationId
    advisor_id: AdvisorId
    advisor_name: str
    client_name: str
    messages: list[Message] = field(default_factory=list)
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def create(cls, advisor_id: AdvisorId, advisor_name: str, client_name: str) -> "Conversation":
        return cls(
            id=ConversationId.generate(),
            advisor_id=advisor_id,
            advisor_name=advisor_name,
            client_name=client_name,
        )

    def add_message(self, sender: MessageSender, text: str) -> Message:
        message = Message(sender=sender, text=text)
        self.messages.append(message)
        return message

    @property
    def message_count(self) -> int:
        return len(self.messages)

    @property
    def last_message(self) -> Message:
        if not self.messages:
            raise EmptyConversationError("La conversación no tiene mensajes")
        return self.messages[-1]

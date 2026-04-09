from abc import ABC, abstractmethod

from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import ConversationId


class ConversationRepository(ABC):
    @abstractmethod
    async def save(self, conversation: Conversation) -> None:
        pass

    @abstractmethod
    async def find_by_id(self, conversation_id: ConversationId) -> Conversation | None:
        pass

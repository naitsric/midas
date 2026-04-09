from abc import ABC, abstractmethod

from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import ConversationId


class ConversationRepository(ABC):
    @abstractmethod
    async def save(self, conversation: Conversation) -> None:
        pass

    @abstractmethod
    async def find_by_id(self, conversation_id: ConversationId) -> Conversation | None:
        pass

    @abstractmethod
    async def find_by_id_and_advisor(
        self, conversation_id: ConversationId, advisor_id: AdvisorId
    ) -> Conversation | None:
        pass

    @abstractmethod
    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[Conversation]:
        pass

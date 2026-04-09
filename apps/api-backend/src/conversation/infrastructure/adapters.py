from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.entities import Conversation
from src.conversation.domain.ports import ConversationRepository
from src.conversation.domain.value_objects import ConversationId


class InMemoryConversationRepository(ConversationRepository):
    def __init__(self):
        self._store: dict[ConversationId, Conversation] = {}

    async def save(self, conversation: Conversation) -> None:
        self._store[conversation.id] = conversation

    async def find_by_id(self, conversation_id: ConversationId) -> Conversation | None:
        return self._store.get(conversation_id)

    async def find_by_id_and_advisor(
        self, conversation_id: ConversationId, advisor_id: AdvisorId
    ) -> Conversation | None:
        conv = self._store.get(conversation_id)
        if conv is not None and conv.advisor_id == advisor_id:
            return conv
        return None

    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[Conversation]:
        return [c for c in self._store.values() if c.advisor_id == advisor_id]

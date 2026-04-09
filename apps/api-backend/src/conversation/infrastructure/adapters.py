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

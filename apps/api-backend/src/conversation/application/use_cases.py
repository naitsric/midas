from src.conversation.domain.entities import Conversation
from src.conversation.domain.ports import ConversationRepository
from src.conversation.domain.value_objects import ConversationId


class SaveConversation:
    def __init__(self, repository: ConversationRepository):
        self._repository = repository

    async def execute(self, conversation: Conversation) -> None:
        await self._repository.save(conversation)


class GetConversation:
    def __init__(self, repository: ConversationRepository):
        self._repository = repository

    async def execute(self, conversation_id: ConversationId) -> Conversation | None:
        return await self._repository.find_by_id(conversation_id)

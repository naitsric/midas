import pytest

from src.conversation.application.use_cases import GetConversation, SaveConversation
from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import ConversationId, MessageSender
from src.conversation.infrastructure.adapters import InMemoryConversationRepository


@pytest.fixture
def repository():
    return InMemoryConversationRepository()


@pytest.fixture
def save_conversation(repository):
    return SaveConversation(repository)


@pytest.fixture
def get_conversation(repository):
    return GetConversation(repository)


class TestSaveConversation:
    @pytest.mark.asyncio
    async def test_save_new_conversation(self, save_conversation, get_conversation):
        conv = Conversation.create(advisor_name="Carlos", client_name="María")
        conv.add_message(sender=MessageSender.advisor("Carlos"), text="Hola")

        await save_conversation.execute(conv)

        found = await get_conversation.execute(conv.id)
        assert found is not None
        assert found.id == conv.id
        assert found.advisor_name == "Carlos"
        assert found.message_count == 1

    @pytest.mark.asyncio
    async def test_save_updates_existing(self, save_conversation, get_conversation):
        conv = Conversation.create(advisor_name="Carlos", client_name="María")
        conv.add_message(sender=MessageSender.advisor("Carlos"), text="Hola")
        await save_conversation.execute(conv)

        conv.add_message(sender=MessageSender.client("María"), text="Necesito un crédito")
        await save_conversation.execute(conv)

        found = await get_conversation.execute(conv.id)
        assert found.message_count == 2


class TestGetConversation:
    @pytest.mark.asyncio
    async def test_get_existing(self, save_conversation, get_conversation):
        conv = Conversation.create(advisor_name="Carlos", client_name="María")
        await save_conversation.execute(conv)

        found = await get_conversation.execute(conv.id)
        assert found is not None
        assert found.client_name == "María"

    @pytest.mark.asyncio
    async def test_get_nonexistent_returns_none(self, get_conversation):
        result = await get_conversation.execute(ConversationId.generate())
        assert result is None

import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.conversation.application.use_cases import GetConversation, ListConversations, SaveConversation
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
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        conv.add_message(sender=MessageSender.advisor("Carlos"), text="Hola")

        await save_conversation.execute(conv)

        found = await get_conversation.execute(conv.id)
        assert found is not None
        assert found.id == conv.id
        assert found.advisor_name == "Carlos"
        assert found.message_count == 1

    @pytest.mark.asyncio
    async def test_save_updates_existing(self, save_conversation, get_conversation):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        conv.add_message(sender=MessageSender.advisor("Carlos"), text="Hola")
        await save_conversation.execute(conv)

        conv.add_message(sender=MessageSender.client("María"), text="Necesito un crédito")
        await save_conversation.execute(conv)

        found = await get_conversation.execute(conv.id)
        assert found.message_count == 2


class TestGetConversation:
    @pytest.mark.asyncio
    async def test_get_existing(self, save_conversation, get_conversation):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        await save_conversation.execute(conv)

        found = await get_conversation.execute(conv.id)
        assert found is not None
        assert found.client_name == "María"

    @pytest.mark.asyncio
    async def test_get_nonexistent_returns_none(self, get_conversation):
        result = await get_conversation.execute(ConversationId.generate())
        assert result is None


class TestListConversations:
    @pytest.mark.asyncio
    async def test_list_empty(self, repository):
        use_case = ListConversations(repository)
        result = await use_case.execute(AdvisorId.generate())
        assert result == []

    @pytest.mark.asyncio
    async def test_list_returns_only_advisor_conversations(self, repository):
        advisor_1 = AdvisorId.generate()
        advisor_2 = AdvisorId.generate()

        conv1 = Conversation.create(advisor_id=advisor_1, advisor_name="Carlos", client_name="María")
        conv2 = Conversation.create(advisor_id=advisor_1, advisor_name="Carlos", client_name="Pedro")
        conv3 = Conversation.create(advisor_id=advisor_2, advisor_name="Ana", client_name="Luis")

        for conv in [conv1, conv2, conv3]:
            await repository.save(conv)

        use_case = ListConversations(repository)
        result = await use_case.execute(advisor_1)

        assert len(result) == 2
        ids = {c.id for c in result}
        assert conv1.id in ids
        assert conv2.id in ids
        assert conv3.id not in ids

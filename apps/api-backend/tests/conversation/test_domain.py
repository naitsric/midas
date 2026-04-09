from datetime import UTC, datetime
from uuid import uuid4

import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.entities import Conversation, Message
from src.conversation.domain.exceptions import EmptyConversationError, InvalidMessageError
from src.conversation.domain.value_objects import ConversationId, MessageSender


class TestMessageSender:
    def test_create_advisor(self):
        sender = MessageSender.advisor("Carlos")
        assert sender.name == "Carlos"
        assert sender.is_advisor is True

    def test_create_client(self):
        sender = MessageSender.client("María")
        assert sender.name == "María"
        assert sender.is_advisor is False

    def test_empty_name_raises(self):
        with pytest.raises(ValueError, match="nombre"):
            MessageSender.advisor("")

    def test_whitespace_name_raises(self):
        with pytest.raises(ValueError, match="nombre"):
            MessageSender.client("   ")


class TestConversationId:
    def test_create_with_uuid(self):
        uid = uuid4()
        cid = ConversationId(uid)
        assert cid.value == uid

    def test_generate_creates_unique_ids(self):
        id1 = ConversationId.generate()
        id2 = ConversationId.generate()
        assert id1 != id2

    def test_equality(self):
        uid = uuid4()
        assert ConversationId(uid) == ConversationId(uid)

    def test_inequality(self):
        assert ConversationId.generate() != ConversationId.generate()


class TestMessage:
    def test_create_message(self):
        sender = MessageSender.advisor("Carlos")
        msg = Message(sender=sender, text="Hola, ¿en qué te puedo ayudar?")
        assert msg.sender == sender
        assert msg.text == "Hola, ¿en qué te puedo ayudar?"
        assert msg.timestamp is not None

    def test_create_with_timestamp(self):
        now = datetime.now(UTC)
        sender = MessageSender.client("María")
        msg = Message(sender=sender, text="Necesito un crédito", timestamp=now)
        assert msg.timestamp == now

    def test_empty_text_raises(self):
        sender = MessageSender.advisor("Carlos")
        with pytest.raises(InvalidMessageError, match="texto"):
            Message(sender=sender, text="")

    def test_whitespace_text_raises(self):
        sender = MessageSender.advisor("Carlos")
        with pytest.raises(InvalidMessageError, match="texto"):
            Message(sender=sender, text="   ")


class TestConversation:
    def _advisor(self):
        return MessageSender.advisor("Carlos")

    def _client(self):
        return MessageSender.client("María")

    def test_create_conversation(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        assert conv.id is not None
        assert conv.advisor_name == "Carlos"
        assert conv.client_name == "María"
        assert len(conv.messages) == 0

    def test_add_message(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        conv.add_message(sender=self._advisor(), text="Hola")
        assert len(conv.messages) == 1
        assert conv.messages[0].text == "Hola"

    def test_add_multiple_messages(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        conv.add_message(sender=self._advisor(), text="Hola")
        conv.add_message(sender=self._client(), text="Necesito un crédito hipotecario")
        conv.add_message(sender=self._advisor(), text="Claro, ¿de cuánto?")
        assert len(conv.messages) == 3

    def test_message_count(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        assert conv.message_count == 0
        conv.add_message(sender=self._advisor(), text="Hola")
        assert conv.message_count == 1

    def test_last_message(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        conv.add_message(sender=self._advisor(), text="Hola")
        conv.add_message(sender=self._client(), text="Necesito un crédito")
        assert conv.last_message.text == "Necesito un crédito"

    def test_last_message_empty_raises(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
        with pytest.raises(EmptyConversationError):
            _ = conv.last_message

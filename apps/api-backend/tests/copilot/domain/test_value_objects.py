import pytest

from src.copilot.domain.value_objects import CopilotMessage, MessageRole, SourceRef, SourceType


class TestCopilotMessage:
    def test_creates_user_message(self):
        msg = CopilotMessage(role=MessageRole.USER, text="hola")
        assert msg.role == MessageRole.USER
        assert msg.text == "hola"

    def test_rejects_empty_text(self):
        with pytest.raises(ValueError, match="vacío"):
            CopilotMessage(role=MessageRole.USER, text="")

    def test_rejects_whitespace_only(self):
        with pytest.raises(ValueError, match="vacío"):
            CopilotMessage(role=MessageRole.USER, text="   \n\t  ")

    def test_is_immutable(self):
        msg = CopilotMessage(role=MessageRole.ASSISTANT, text="hi")
        with pytest.raises(Exception):  # frozen dataclass raises FrozenInstanceError
            msg.text = "changed"  # type: ignore[misc]


class TestSourceRef:
    def test_creates_call_source(self):
        ref = SourceRef(type=SourceType.CALL, label="Llamada · Juan Pérez")
        assert ref.type == SourceType.CALL
        assert "Juan" in ref.label

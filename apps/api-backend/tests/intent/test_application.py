import pytest

from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import MessageSender
from src.intent.application.use_cases import DetectFinancialIntent
from src.intent.domain.entities import IntentResult
from src.intent.domain.exceptions import IntentDetectionError
from src.intent.domain.ports import IntentDetector
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType


class FakeIntentDetector(IntentDetector):
    """Adapter fake que retorna resultados predefinidos para tests."""

    def __init__(self, result: IntentResult):
        self._result = result

    async def detect(self, conversation: Conversation) -> IntentResult:
        return self._result


class FailingIntentDetector(IntentDetector):
    """Adapter fake que siempre falla."""

    async def detect(self, conversation: Conversation) -> IntentResult:
        raise IntentDetectionError("Error de conexión con el modelo")


def _conversation_with_messages() -> Conversation:
    conv = Conversation.create(advisor_name="Carlos", client_name="María")
    conv.add_message(sender=MessageSender.advisor("Carlos"), text="Hola María, ¿en qué te puedo ayudar?")
    conv.add_message(sender=MessageSender.client("María"), text="Estoy buscando un crédito hipotecario de 250 millones")
    conv.add_message(sender=MessageSender.advisor("Carlos"), text="Perfecto, ¿a cuántos años lo quieres?")
    conv.add_message(sender=MessageSender.client("María"), text="A 20 años, en Bogotá")
    return conv


class TestDetectFinancialIntent:
    @pytest.mark.asyncio
    async def test_detect_mortgage_intent(self):
        expected = IntentResult.detected(
            product_type=ProductType.MORTGAGE,
            confidence=Confidence(0.95),
            entities=ExtractedEntities(amount="250,000,000 COP", term="20 años", location="Bogotá"),
            summary="El cliente busca un crédito hipotecario",
        )
        detector = FakeIntentDetector(expected)
        use_case = DetectFinancialIntent(detector)

        result = await use_case.execute(_conversation_with_messages())

        assert result.intent_detected is True
        assert result.product_type == ProductType.MORTGAGE
        assert result.confidence.value == 0.95
        assert result.is_actionable is True

    @pytest.mark.asyncio
    async def test_detect_no_intent(self):
        expected = IntentResult.not_detected(summary="Conversación casual")
        detector = FakeIntentDetector(expected)
        use_case = DetectFinancialIntent(detector)

        result = await use_case.execute(_conversation_with_messages())

        assert result.intent_detected is False
        assert result.is_actionable is False

    @pytest.mark.asyncio
    async def test_detect_raises_on_empty_conversation(self):
        detector = FakeIntentDetector(IntentResult.not_detected(summary=""))
        use_case = DetectFinancialIntent(detector)

        conv = Conversation.create(advisor_name="Carlos", client_name="María")

        with pytest.raises(IntentDetectionError, match="mensajes"):
            await use_case.execute(conv)

    @pytest.mark.asyncio
    async def test_detect_propagates_detector_error(self):
        detector = FailingIntentDetector()
        use_case = DetectFinancialIntent(detector)

        with pytest.raises(IntentDetectionError, match="conexión"):
            await use_case.execute(_conversation_with_messages())

import os

import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import MessageSender
from src.intent.domain.value_objects import ProductType
from src.intent.infrastructure.gemini_adapter import GeminiIntentDetector


def _get_api_key() -> str:
    key = os.getenv("GEMINI_API_KEY", "")
    if not key:
        pytest.skip("GEMINI_API_KEY no configurada")
    return key


def _mortgage_conversation() -> Conversation:
    conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="María")
    conv.add_message(
        sender=MessageSender.advisor("Carlos"),
        text="Hola María, ¿en qué te puedo ayudar hoy?",
    )
    conv.add_message(
        sender=MessageSender.client("María"),
        text="Hola Carlos, estoy buscando un crédito hipotecario para comprar un apartamento en Bogotá. "
        "Necesito aproximadamente 250 millones de pesos a 20 años.",
    )
    conv.add_message(
        sender=MessageSender.advisor("Carlos"),
        text="Perfecto, ¿ya tienes el apartamento identificado o estás en búsqueda?",
    )
    conv.add_message(
        sender=MessageSender.client("María"),
        text="Ya lo tengo identificado, es en el norte de Bogotá. Mi salario es de 8 millones mensuales.",
    )
    return conv


def _casual_conversation() -> Conversation:
    conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="Pedro")
    conv.add_message(
        sender=MessageSender.advisor("Carlos"),
        text="Hola Pedro, ¿cómo estás?",
    )
    conv.add_message(
        sender=MessageSender.client("Pedro"),
        text="Bien Carlos, solo te escribía para saludarte. ¿Cómo va todo?",
    )
    conv.add_message(
        sender=MessageSender.advisor("Carlos"),
        text="Todo bien, gracias. Un abrazo.",
    )
    return conv


@pytest.mark.integration
class TestGeminiIntentDetectorReal:
    @pytest.mark.asyncio
    async def test_detect_mortgage_intent(self):
        detector = GeminiIntentDetector(api_key=_get_api_key())
        result = await detector.detect(_mortgage_conversation())

        assert result.intent_detected is True
        assert result.product_type == ProductType.MORTGAGE
        assert result.confidence.value >= 0.7
        assert result.is_actionable is True
        assert result.entities.amount is not None

    @pytest.mark.asyncio
    async def test_detect_no_intent_casual(self):
        detector = GeminiIntentDetector(api_key=_get_api_key())
        result = await detector.detect(_casual_conversation())

        assert result.intent_detected is False
        assert result.is_actionable is False

    @pytest.mark.asyncio
    async def test_detect_auto_loan_intent(self):
        conv = Conversation.create(advisor_id=AdvisorId.generate(), advisor_name="Carlos", client_name="Luis")
        conv.add_message(
            sender=MessageSender.client("Luis"),
            text="Carlos, necesito financiar un carro nuevo. Estoy mirando una camioneta de unos 80 millones. "
            "¿Qué opciones de crédito vehicular tienes?",
        )
        detector = GeminiIntentDetector(api_key=_get_api_key())
        result = await detector.detect(conv)

        assert result.intent_detected is True
        assert result.product_type == ProductType.AUTO_LOAN
        assert result.confidence.value >= 0.7

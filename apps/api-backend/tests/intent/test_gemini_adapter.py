import json

import pytest

from src.intent.domain.exceptions import IntentDetectionError
from src.intent.domain.value_objects import ProductType
from src.intent.infrastructure.gemini_adapter import GeminiIntentDetector


class TestGeminiResponseParsing:
    """Tests del parsing de respuestas de Gemini sin llamar al API real."""

    def _parser(self):
        adapter = object.__new__(GeminiIntentDetector)
        return adapter

    def test_parse_detected_mortgage(self):
        raw = json.dumps(
            {
                "intent_detected": True,
                "confidence": 0.95,
                "product_type": "mortgage",
                "entities": {"amount": "250M COP", "term": "20 años", "location": "Bogotá"},
                "summary": "Busca hipotecario",
            }
        )
        result = self._parser()._parse_response(raw)

        assert result.intent_detected is True
        assert result.product_type == ProductType.MORTGAGE
        assert result.confidence.value == 0.95
        assert result.entities.amount == "250M COP"
        assert result.entities.location == "Bogotá"

    def test_parse_detected_auto_loan(self):
        raw = json.dumps(
            {
                "intent_detected": True,
                "confidence": 0.8,
                "product_type": "auto_loan",
                "entities": {"amount": "30M COP"},
                "summary": "Quiere carro",
            }
        )
        result = self._parser()._parse_response(raw)

        assert result.product_type == ProductType.AUTO_LOAN

    def test_parse_not_detected(self):
        raw = json.dumps(
            {
                "intent_detected": False,
                "confidence": 0.1,
                "product_type": None,
                "entities": {},
                "summary": "Chat casual",
            }
        )
        result = self._parser()._parse_response(raw)

        assert result.intent_detected is False
        assert result.product_type is None

    def test_parse_invalid_json_raises(self):
        with pytest.raises(IntentDetectionError, match="JSON"):
            self._parser()._parse_response("esto no es json")

    def test_parse_unknown_product_type_raises(self):
        raw = json.dumps(
            {
                "intent_detected": True,
                "confidence": 0.9,
                "product_type": "crypto_loan",
                "entities": {},
                "summary": "Algo raro",
            }
        )
        with pytest.raises(IntentDetectionError, match="no reconocido"):
            self._parser()._parse_response(raw)

    def test_parse_missing_entities_defaults(self):
        raw = json.dumps(
            {
                "intent_detected": True,
                "confidence": 0.7,
                "product_type": "insurance",
                "summary": "Busca seguro",
            }
        )
        result = self._parser()._parse_response(raw)

        assert result.entities.amount is None
        assert result.entities.term is None
        assert result.entities.location is None

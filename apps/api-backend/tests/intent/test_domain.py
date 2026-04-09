import pytest

from src.intent.domain.entities import IntentResult
from src.intent.domain.exceptions import InvalidConfidenceError
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType


class TestProductType:
    def test_all_types_exist(self):
        assert ProductType.MORTGAGE is not None
        assert ProductType.AUTO_LOAN is not None
        assert ProductType.REFINANCE is not None
        assert ProductType.INSURANCE is not None

    def test_label_in_spanish(self):
        assert ProductType.MORTGAGE.label == "Crédito Hipotecario"
        assert ProductType.AUTO_LOAN.label == "Crédito Vehicular"
        assert ProductType.REFINANCE.label == "Refinanciación"
        assert ProductType.INSURANCE.label == "Seguro"


class TestConfidence:
    def test_create_valid(self):
        c = Confidence(0.95)
        assert c.value == 0.95

    def test_zero_is_valid(self):
        c = Confidence(0.0)
        assert c.value == 0.0

    def test_one_is_valid(self):
        c = Confidence(1.0)
        assert c.value == 1.0

    def test_negative_raises(self):
        with pytest.raises(InvalidConfidenceError):
            Confidence(-0.1)

    def test_above_one_raises(self):
        with pytest.raises(InvalidConfidenceError):
            Confidence(1.1)

    def test_is_high_above_threshold(self):
        assert Confidence(0.8).is_high is True

    def test_is_high_below_threshold(self):
        assert Confidence(0.5).is_high is False

    def test_is_high_at_threshold(self):
        assert Confidence(0.7).is_high is True


class TestExtractedEntities:
    def test_create_with_all_fields(self):
        entities = ExtractedEntities(
            amount="250,000 USD",
            term="20 años",
            location="Bogotá",
            additional={"income": "5,000 USD/mes"},
        )
        assert entities.amount == "250,000 USD"
        assert entities.term == "20 años"
        assert entities.location == "Bogotá"
        assert entities.additional["income"] == "5,000 USD/mes"

    def test_create_with_no_fields(self):
        entities = ExtractedEntities()
        assert entities.amount is None
        assert entities.term is None
        assert entities.location is None
        assert entities.additional == {}

    def test_has_data_when_populated(self):
        entities = ExtractedEntities(amount="100,000 USD")
        assert entities.has_data is True

    def test_has_data_when_empty(self):
        entities = ExtractedEntities()
        assert entities.has_data is False

    def test_has_data_with_only_additional(self):
        entities = ExtractedEntities(additional={"key": "value"})
        assert entities.has_data is True


class TestIntentResult:
    def test_create_detected(self):
        result = IntentResult.detected(
            product_type=ProductType.MORTGAGE,
            confidence=Confidence(0.95),
            entities=ExtractedEntities(amount="250,000 USD"),
            summary="El cliente pregunta por tasas de crédito hipotecario",
        )
        assert result.intent_detected is True
        assert result.product_type == ProductType.MORTGAGE
        assert result.confidence.value == 0.95
        assert result.summary == "El cliente pregunta por tasas de crédito hipotecario"

    def test_create_not_detected(self):
        result = IntentResult.not_detected(summary="Conversación casual sin intención financiera")
        assert result.intent_detected is False
        assert result.product_type is None
        assert result.confidence.value == 0.0
        assert result.entities.has_data is False

    def test_is_actionable_high_confidence(self):
        result = IntentResult.detected(
            product_type=ProductType.AUTO_LOAN,
            confidence=Confidence(0.85),
            entities=ExtractedEntities(amount="30,000 USD"),
            summary="Busca crédito vehicular",
        )
        assert result.is_actionable is True

    def test_is_not_actionable_low_confidence(self):
        result = IntentResult.detected(
            product_type=ProductType.AUTO_LOAN,
            confidence=Confidence(0.4),
            entities=ExtractedEntities(),
            summary="Mención vaga de carro",
        )
        assert result.is_actionable is False

    def test_is_not_actionable_when_not_detected(self):
        result = IntentResult.not_detected(summary="Sin intención")
        assert result.is_actionable is False

from dataclasses import dataclass

from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType


@dataclass(frozen=True)
class IntentResult:
    intent_detected: bool
    confidence: Confidence
    product_type: ProductType | None
    entities: ExtractedEntities
    summary: str

    @classmethod
    def detected(
        cls,
        product_type: ProductType,
        confidence: Confidence,
        entities: ExtractedEntities,
        summary: str,
    ) -> "IntentResult":
        return cls(
            intent_detected=True,
            confidence=confidence,
            product_type=product_type,
            entities=entities,
            summary=summary,
        )

    @classmethod
    def not_detected(cls, summary: str) -> "IntentResult":
        return cls(
            intent_detected=False,
            confidence=Confidence(0.0),
            product_type=None,
            entities=ExtractedEntities(),
            summary=summary,
        )

    @property
    def is_actionable(self) -> bool:
        return self.intent_detected and self.confidence.is_high

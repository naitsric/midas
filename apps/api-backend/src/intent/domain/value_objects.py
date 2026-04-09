from dataclasses import dataclass, field
from enum import Enum

from src.intent.domain.exceptions import InvalidConfidenceError


class ProductType(Enum):
    MORTGAGE = "mortgage"
    AUTO_LOAN = "auto_loan"
    REFINANCE = "refinance"
    INSURANCE = "insurance"

    @property
    def label(self) -> str:
        labels = {
            ProductType.MORTGAGE: "Crédito Hipotecario",
            ProductType.AUTO_LOAN: "Crédito Vehicular",
            ProductType.REFINANCE: "Refinanciación",
            ProductType.INSURANCE: "Seguro",
        }
        return labels[self]


@dataclass(frozen=True)
class Confidence:
    value: float

    def __post_init__(self):
        if self.value < 0.0 or self.value > 1.0:
            raise InvalidConfidenceError(f"Confidence debe estar entre 0 y 1, recibido: {self.value}")

    @property
    def is_high(self) -> bool:
        return self.value >= 0.7


@dataclass(frozen=True)
class ExtractedEntities:
    amount: str | None = None
    term: str | None = None
    location: str | None = None
    additional: dict[str, str] = field(default_factory=dict)

    @property
    def has_data(self) -> bool:
        return self.amount is not None or self.term is not None or self.location is not None or len(self.additional) > 0

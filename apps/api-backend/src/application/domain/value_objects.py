from dataclasses import dataclass
from enum import Enum
from uuid import UUID, uuid4

from src.intent.domain.value_objects import ProductType


class ApplicationStatus(Enum):
    DRAFT = "draft"
    REVIEW = "review"
    SUBMITTED = "submitted"
    REJECTED = "rejected"

    @property
    def label(self) -> str:
        labels = {
            ApplicationStatus.DRAFT: "Borrador",
            ApplicationStatus.REVIEW: "En Revisión",
            ApplicationStatus.SUBMITTED: "Enviada",
            ApplicationStatus.REJECTED: "Rechazada",
        }
        return labels[self]


@dataclass(frozen=True)
class ApplicationId:
    value: UUID

    @classmethod
    def generate(cls) -> "ApplicationId":
        return cls(value=uuid4())

    def __str__(self) -> str:
        return str(self.value)


@dataclass(frozen=True)
class ApplicantData:
    full_name: str
    phone: str | None = None
    estimated_income: str | None = None
    employment_type: str | None = None

    def __post_init__(self):
        if not self.full_name or not self.full_name.strip():
            raise ValueError("El nombre del solicitante no puede estar vacío")

    @property
    def completeness(self) -> float:
        fields = [self.full_name, self.phone, self.estimated_income, self.employment_type]
        filled = sum(1 for f in fields if f is not None)
        return filled / len(fields)


@dataclass(frozen=True)
class ProductRequest:
    product_type: ProductType
    amount: str | None = None
    term: str | None = None
    location: str | None = None

    @property
    def summary(self) -> str:
        parts = [self.product_type.label]
        if self.amount:
            parts.append(f"por {self.amount}")
        if self.term:
            parts.append(f"a {self.term}")
        if self.location:
            parts.append(f"en {self.location}")
        return " ".join(parts)

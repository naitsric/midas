import secrets
from dataclasses import dataclass
from enum import Enum
from uuid import UUID, uuid4


class AdvisorStatus(Enum):
    ACTIVE = "active"
    SUSPENDED = "suspended"
    DEACTIVATED = "deactivated"

    @property
    def label(self) -> str:
        labels = {
            AdvisorStatus.ACTIVE: "Activo",
            AdvisorStatus.SUSPENDED: "Suspendido",
            AdvisorStatus.DEACTIVATED: "Desactivado",
        }
        return labels[self]


@dataclass(frozen=True)
class AdvisorId:
    value: UUID

    @classmethod
    def generate(cls) -> "AdvisorId":
        return cls(value=uuid4())

    def __str__(self) -> str:
        return str(self.value)


@dataclass(frozen=True)
class ApiKey:
    value: str

    @classmethod
    def generate(cls) -> "ApiKey":
        token = secrets.token_urlsafe(32)
        return cls(value=f"midas_{token}")

    @property
    def masked(self) -> str:
        return self.value[:10] + "****"

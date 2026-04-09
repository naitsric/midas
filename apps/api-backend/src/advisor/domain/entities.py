from dataclasses import dataclass, field
from datetime import UTC, datetime

from src.advisor.domain.exceptions import InvalidAdvisorError
from src.advisor.domain.value_objects import AdvisorId, AdvisorStatus, ApiKey


@dataclass
class Advisor:
    id: AdvisorId
    name: str
    email: str
    phone: str
    status: AdvisorStatus
    api_key: ApiKey
    suspension_reason: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def register(cls, name: str, email: str, phone: str) -> "Advisor":
        if not name or not name.strip():
            raise InvalidAdvisorError("El nombre del asesor no puede estar vacío")
        if not email or not email.strip():
            raise InvalidAdvisorError("El email del asesor no puede estar vacío")

        return cls(
            id=AdvisorId.generate(),
            name=name.strip(),
            email=email.strip(),
            phone=phone.strip(),
            status=AdvisorStatus.ACTIVE,
            api_key=ApiKey.generate(),
        )

    @property
    def is_active(self) -> bool:
        return self.status == AdvisorStatus.ACTIVE

    def suspend(self, reason: str) -> None:
        if self.status == AdvisorStatus.DEACTIVATED:
            raise InvalidAdvisorError("No se puede suspender un asesor desactivado")
        if self.status == AdvisorStatus.SUSPENDED:
            raise InvalidAdvisorError("No se puede suspender desde estado Suspendido")
        self.status = AdvisorStatus.SUSPENDED
        self.suspension_reason = reason

    def reactivate(self) -> None:
        if self.status != AdvisorStatus.SUSPENDED:
            raise InvalidAdvisorError("Solo se puede reactivar un asesor suspendido")
        self.status = AdvisorStatus.ACTIVE
        self.suspension_reason = None

    def deactivate(self) -> None:
        if self.status == AdvisorStatus.DEACTIVATED:
            raise InvalidAdvisorError("El asesor ya está desactivado")
        self.status = AdvisorStatus.DEACTIVATED

    def regenerate_api_key(self) -> None:
        self.api_key = ApiKey.generate()

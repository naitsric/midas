from dataclasses import dataclass, field
from datetime import UTC, datetime

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.exceptions import InvalidApplicationError
from src.application.domain.value_objects import ApplicantData, ApplicationId, ApplicationStatus, ProductRequest


@dataclass
class CreditApplication:
    id: ApplicationId
    advisor_id: AdvisorId
    applicant: ApplicantData
    product_request: ProductRequest
    conversation_summary: str
    status: ApplicationStatus = ApplicationStatus.DRAFT
    rejection_reason: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def create(
        cls,
        advisor_id: AdvisorId,
        applicant: ApplicantData,
        product_request: ProductRequest,
        conversation_summary: str,
    ) -> "CreditApplication":
        return cls(
            id=ApplicationId.generate(),
            advisor_id=advisor_id,
            applicant=applicant,
            product_request=product_request,
            conversation_summary=conversation_summary,
        )

    def submit_for_review(self) -> None:
        if self.status != ApplicationStatus.DRAFT:
            raise InvalidApplicationError(f"No se puede enviar a revisión desde estado {self.status.label}")
        self.status = ApplicationStatus.REVIEW

    def mark_submitted(self) -> None:
        if self.status != ApplicationStatus.REVIEW:
            raise InvalidApplicationError(f"No se puede marcar como enviada desde estado {self.status.label}")
        self.status = ApplicationStatus.SUBMITTED

    def reject(self, reason: str) -> None:
        if self.status not in (ApplicationStatus.DRAFT, ApplicationStatus.REVIEW):
            raise InvalidApplicationError(f"No se puede rechazar desde estado {self.status.label}")
        self.status = ApplicationStatus.REJECTED
        self.rejection_reason = reason

    def update_applicant(self, applicant: ApplicantData) -> None:
        if self.status in (ApplicationStatus.SUBMITTED, ApplicationStatus.REJECTED):
            raise InvalidApplicationError("No se puede modificar una solicitud enviada o rechazada")
        self.applicant = applicant

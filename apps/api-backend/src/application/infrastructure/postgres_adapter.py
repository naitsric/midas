from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.ports import ApplicationRepository
from src.application.domain.value_objects import ApplicantData, ApplicationId, ApplicationStatus, ProductRequest
from src.intent.domain.value_objects import ProductType
from src.shared.infrastructure.database import Database


class PostgresApplicationRepository(ApplicationRepository):
    def __init__(self, db: Database):
        self._db = db

    async def save(self, application: CreditApplication) -> None:
        await self._db.execute(
            """
            INSERT INTO credit_applications (
                id, advisor_id, status,
                applicant_full_name, applicant_phone, applicant_estimated_income, applicant_employment_type,
                product_type, product_amount, product_term, product_location,
                conversation_summary, rejection_reason, created_at
            )
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                applicant_full_name = EXCLUDED.applicant_full_name,
                applicant_phone = EXCLUDED.applicant_phone,
                applicant_estimated_income = EXCLUDED.applicant_estimated_income,
                applicant_employment_type = EXCLUDED.applicant_employment_type,
                rejection_reason = EXCLUDED.rejection_reason
            """,
            application.id.value,
            application.advisor_id.value,
            application.status.value,
            application.applicant.full_name,
            application.applicant.phone,
            application.applicant.estimated_income,
            application.applicant.employment_type,
            application.product_request.product_type.value,
            application.product_request.amount,
            application.product_request.term,
            application.product_request.location,
            application.conversation_summary,
            application.rejection_reason,
            application.created_at,
        )

    async def find_by_id(self, application_id: ApplicationId) -> CreditApplication | None:
        row = await self._db.fetchrow("SELECT * FROM credit_applications WHERE id = $1", application_id.value)
        return self._to_entity(row) if row else None

    async def find_by_id_and_advisor(
        self, application_id: ApplicationId, advisor_id: AdvisorId
    ) -> CreditApplication | None:
        row = await self._db.fetchrow(
            "SELECT * FROM credit_applications WHERE id = $1 AND advisor_id = $2",
            application_id.value,
            advisor_id.value,
        )
        return self._to_entity(row) if row else None

    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[CreditApplication]:
        rows = await self._db.fetch(
            "SELECT * FROM credit_applications WHERE advisor_id = $1 ORDER BY created_at DESC",
            advisor_id.value,
        )
        return [self._to_entity(row) for row in rows]

    @staticmethod
    def _to_entity(row) -> CreditApplication:
        return CreditApplication(
            id=ApplicationId(row["id"]),
            advisor_id=AdvisorId(row["advisor_id"]),
            applicant=ApplicantData(
                full_name=row["applicant_full_name"],
                phone=row["applicant_phone"],
                estimated_income=row["applicant_estimated_income"],
                employment_type=row["applicant_employment_type"],
            ),
            product_request=ProductRequest(
                product_type=ProductType(row["product_type"]),
                amount=row["product_amount"],
                term=row["product_term"],
                location=row["product_location"],
            ),
            conversation_summary=row["conversation_summary"],
            status=ApplicationStatus(row["status"]),
            rejection_reason=row["rejection_reason"],
            created_at=row["created_at"],
        )

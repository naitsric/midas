from src.advisor.domain.entities import Advisor
from src.advisor.domain.ports import AdvisorRepository
from src.advisor.domain.value_objects import AdvisorId, AdvisorStatus, ApiKey
from src.shared.infrastructure.database import Database


class PostgresAdvisorRepository(AdvisorRepository):
    def __init__(self, db: Database):
        self._db = db

    async def save(self, advisor: Advisor) -> None:
        await self._db.execute(
            """
            INSERT INTO advisors (id, name, email, phone, status, api_key, suspension_reason,
                                  voip_endpoint_arn, voip_device_token, created_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                status = EXCLUDED.status,
                api_key = EXCLUDED.api_key,
                suspension_reason = EXCLUDED.suspension_reason,
                voip_endpoint_arn = EXCLUDED.voip_endpoint_arn,
                voip_device_token = EXCLUDED.voip_device_token
            """,
            advisor.id.value,
            advisor.name,
            advisor.email,
            advisor.phone,
            advisor.status.value,
            advisor.api_key.value,
            advisor.suspension_reason,
            advisor.voip_endpoint_arn,
            advisor.voip_device_token,
            advisor.created_at,
        )

    async def find_by_id(self, advisor_id: AdvisorId) -> Advisor | None:
        row = await self._db.fetchrow("SELECT * FROM advisors WHERE id = $1", advisor_id.value)
        return self._to_entity(row) if row else None

    async def find_by_api_key(self, api_key: ApiKey) -> Advisor | None:
        row = await self._db.fetchrow("SELECT * FROM advisors WHERE api_key = $1", api_key.value)
        return self._to_entity(row) if row else None

    async def find_by_email(self, email: str) -> Advisor | None:
        row = await self._db.fetchrow("SELECT * FROM advisors WHERE email = $1", email)
        return self._to_entity(row) if row else None

    @staticmethod
    def _to_entity(row) -> Advisor:
        return Advisor(
            id=AdvisorId(row["id"]),
            name=row["name"],
            email=row["email"],
            phone=row["phone"],
            status=AdvisorStatus(row["status"]),
            api_key=ApiKey(row["api_key"]),
            suspension_reason=row["suspension_reason"],
            voip_endpoint_arn=row["voip_endpoint_arn"],
            voip_device_token=row["voip_device_token"],
            created_at=row["created_at"],
        )

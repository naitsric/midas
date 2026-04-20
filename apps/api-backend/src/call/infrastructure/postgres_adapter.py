from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.entities import CallRecording
from src.call.domain.ports import CallRepository, PhoneMapping, PhoneNumberRepository
from src.call.domain.value_objects import CallId, CallStatus
from src.shared.infrastructure.database import Database


class PostgresCallRepository(CallRepository):
    def __init__(self, db: Database):
        self._db = db

    async def save(self, call: CallRecording) -> None:
        await self._db.execute(
            """
            INSERT INTO call_recordings
                (id, advisor_id, client_name, status, transcript, duration_seconds,
                 voip_call_id, recording_key, recording_url, created_at, completed_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                transcript = EXCLUDED.transcript,
                duration_seconds = EXCLUDED.duration_seconds,
                voip_call_id = EXCLUDED.voip_call_id,
                recording_key = EXCLUDED.recording_key,
                recording_url = EXCLUDED.recording_url,
                completed_at = EXCLUDED.completed_at
            """,
            call.id.value,
            call.advisor_id.value,
            call.client_name,
            call.status.value,
            call.transcript,
            call.duration_seconds,
            call.voip_call_id,
            call.recording_key,
            call.recording_url,
            call.created_at,
            call.completed_at,
        )

    async def find_by_id(self, call_id: CallId) -> CallRecording | None:
        row = await self._db.fetchrow("SELECT * FROM call_recordings WHERE id = $1", call_id.value)
        if not row:
            return None
        return self._to_entity(row)

    async def find_by_id_and_advisor(self, call_id: CallId, advisor_id: AdvisorId) -> CallRecording | None:
        row = await self._db.fetchrow(
            "SELECT * FROM call_recordings WHERE id = $1 AND advisor_id = $2",
            call_id.value,
            advisor_id.value,
        )
        if not row:
            return None
        return self._to_entity(row)

    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[CallRecording]:
        rows = await self._db.fetch(
            "SELECT * FROM call_recordings WHERE advisor_id = $1 ORDER BY created_at DESC",
            advisor_id.value,
        )
        return [self._to_entity(row) for row in rows]

    async def find_by_voip_call_id(self, voip_call_id: str) -> CallRecording | None:
        row = await self._db.fetchrow(
            "SELECT * FROM call_recordings WHERE voip_call_id = $1", voip_call_id
        )
        if not row:
            return None
        return self._to_entity(row)

    @staticmethod
    def _to_entity(row) -> CallRecording:
        return CallRecording(
            id=CallId(row["id"]),
            advisor_id=AdvisorId(row["advisor_id"]),
            client_name=row["client_name"],
            status=CallStatus(row["status"]),
            transcript=row["transcript"] or "",
            duration_seconds=row["duration_seconds"],
            voip_call_id=row.get("voip_call_id"),
            recording_key=row.get("recording_key"),
            recording_url=row.get("recording_url"),
            created_at=row["created_at"],
            completed_at=row["completed_at"],
        )


class PostgresPhoneNumberRepository(PhoneNumberRepository):
    def __init__(self, db: Database):
        self._db = db

    async def save(self, mapping: PhoneMapping) -> None:
        await self._db.execute(
            """
            INSERT INTO advisor_phones (phone_number, advisor_id, provider)
            VALUES ($1, $2, $3)
            ON CONFLICT (phone_number) DO UPDATE SET
                advisor_id = EXCLUDED.advisor_id,
                provider = EXCLUDED.provider
            """,
            mapping.phone_number,
            mapping.advisor_id.value,
            mapping.provider,
        )

    async def find_advisor_by_phone(self, phone_number: str) -> AdvisorId | None:
        row = await self._db.fetchrow(
            "SELECT advisor_id FROM advisor_phones WHERE phone_number = $1",
            phone_number,
        )
        if not row:
            return None
        return AdvisorId(row["advisor_id"])

    async def find_phones_by_advisor(self, advisor_id: AdvisorId) -> list[PhoneMapping]:
        rows = await self._db.fetch(
            "SELECT * FROM advisor_phones WHERE advisor_id = $1",
            advisor_id.value,
        )
        return [
            PhoneMapping(
                phone_number=row["phone_number"],
                advisor_id=AdvisorId(row["advisor_id"]),
                provider=row["provider"],
            )
            for row in rows
        ]

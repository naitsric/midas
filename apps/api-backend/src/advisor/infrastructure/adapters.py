from src.advisor.domain.entities import Advisor
from src.advisor.domain.ports import AdvisorRepository
from src.advisor.domain.value_objects import AdvisorId, ApiKey


class InMemoryAdvisorRepository(AdvisorRepository):
    def __init__(self):
        self._store: dict[AdvisorId, Advisor] = {}

    async def save(self, advisor: Advisor) -> None:
        self._store[advisor.id] = advisor

    async def find_by_id(self, advisor_id: AdvisorId) -> Advisor | None:
        return self._store.get(advisor_id)

    async def find_by_api_key(self, api_key: ApiKey) -> Advisor | None:
        return next((a for a in self._store.values() if a.api_key == api_key), None)

    async def find_by_email(self, email: str) -> Advisor | None:
        return next((a for a in self._store.values() if a.email == email), None)

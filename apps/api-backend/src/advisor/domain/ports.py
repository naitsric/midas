from abc import ABC, abstractmethod

from src.advisor.domain.entities import Advisor
from src.advisor.domain.value_objects import AdvisorId, ApiKey


class AdvisorRepository(ABC):
    @abstractmethod
    async def save(self, advisor: Advisor) -> None:
        pass

    @abstractmethod
    async def find_by_id(self, advisor_id: AdvisorId) -> Advisor | None:
        pass

    @abstractmethod
    async def find_by_api_key(self, api_key: ApiKey) -> Advisor | None:
        pass

    @abstractmethod
    async def find_by_email(self, email: str) -> Advisor | None:
        pass

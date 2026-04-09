from abc import ABC, abstractmethod

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.value_objects import ApplicationId
from src.conversation.domain.entities import Conversation
from src.intent.domain.entities import IntentResult


class ApplicationRepository(ABC):
    @abstractmethod
    async def save(self, application: CreditApplication) -> None:
        pass

    @abstractmethod
    async def find_by_id(self, application_id: ApplicationId) -> CreditApplication | None:
        pass

    @abstractmethod
    async def find_by_id_and_advisor(
        self, application_id: ApplicationId, advisor_id: AdvisorId
    ) -> CreditApplication | None:
        pass

    @abstractmethod
    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[CreditApplication]:
        pass


class ApplicationGenerator(ABC):
    @abstractmethod
    async def generate(
        self, advisor_id: AdvisorId, conversation: Conversation, intent: IntentResult
    ) -> CreditApplication:
        pass

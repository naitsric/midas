from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.ports import ApplicationRepository
from src.application.domain.value_objects import ApplicationId


class InMemoryApplicationRepository(ApplicationRepository):
    def __init__(self):
        self._store: dict[ApplicationId, CreditApplication] = {}

    async def save(self, application: CreditApplication) -> None:
        self._store[application.id] = application

    async def find_by_id(self, application_id: ApplicationId) -> CreditApplication | None:
        return self._store.get(application_id)

    async def find_by_id_and_advisor(
        self, application_id: ApplicationId, advisor_id: AdvisorId
    ) -> CreditApplication | None:
        app = self._store.get(application_id)
        if app is not None and app.advisor_id == advisor_id:
            return app
        return None

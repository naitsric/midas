from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.exceptions import ApplicationGenerationError
from src.application.domain.ports import ApplicationGenerator, ApplicationRepository
from src.application.domain.value_objects import ApplicationId
from src.conversation.domain.entities import Conversation
from src.intent.domain.entities import IntentResult


class GenerateCreditApplication:
    def __init__(self, repository: ApplicationRepository, generator: ApplicationGenerator):
        self._repository = repository
        self._generator = generator

    async def execute(
        self, advisor_id: AdvisorId, conversation: Conversation, intent: IntentResult
    ) -> CreditApplication:
        if not intent.is_actionable:
            raise ApplicationGenerationError(
                "No se puede generar solicitud: la intención no es actionable "
                f"(detected={intent.intent_detected}, confidence={intent.confidence.value})"
            )

        application = await self._generator.generate(advisor_id, conversation, intent)
        await self._repository.save(application)
        return application


class GetCreditApplication:
    def __init__(self, repository: ApplicationRepository):
        self._repository = repository

    async def execute(self, application_id: ApplicationId) -> CreditApplication | None:
        return await self._repository.find_by_id(application_id)


class ListCreditApplications:
    def __init__(self, repository: ApplicationRepository):
        self._repository = repository

    async def execute(self, advisor_id: AdvisorId) -> list[CreditApplication]:
        return await self._repository.find_all_by_advisor(advisor_id)

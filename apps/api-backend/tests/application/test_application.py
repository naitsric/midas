import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.application.application.use_cases import GenerateCreditApplication, GetCreditApplication
from src.application.domain.entities import CreditApplication
from src.application.domain.exceptions import ApplicationGenerationError
from src.application.domain.ports import ApplicationGenerator, ApplicationRepository
from src.application.domain.value_objects import ApplicantData, ApplicationId, ApplicationStatus, ProductRequest
from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import MessageSender
from src.intent.domain.entities import IntentResult
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType

ADVISOR_ID = AdvisorId.generate()


class FakeApplicationRepository(ApplicationRepository):
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


class FakeApplicationGenerator(ApplicationGenerator):
    async def generate(
        self, advisor_id: AdvisorId, conversation: Conversation, intent: IntentResult
    ) -> CreditApplication:
        return CreditApplication.create(
            advisor_id=advisor_id,
            applicant=ApplicantData(
                full_name=conversation.client_name,
                phone=None,
                estimated_income=intent.entities.additional.get("income"),
            ),
            product_request=ProductRequest(
                product_type=intent.product_type,
                amount=intent.entities.amount,
                term=intent.entities.term,
                location=intent.entities.location,
            ),
            conversation_summary=intent.summary,
        )


class FailingApplicationGenerator(ApplicationGenerator):
    async def generate(
        self, advisor_id: AdvisorId, conversation: Conversation, intent: IntentResult
    ) -> CreditApplication:
        raise ApplicationGenerationError("Error al generar solicitud")


def _conversation() -> Conversation:
    conv = Conversation.create(advisor_id=ADVISOR_ID, advisor_name="Carlos", client_name="María García")
    conv.add_message(sender=MessageSender.advisor("Carlos"), text="Hola María")
    conv.add_message(
        sender=MessageSender.client("María García"),
        text="Necesito un crédito hipotecario de 250M en Bogotá a 20 años",
    )
    return conv


def _intent() -> IntentResult:
    return IntentResult.detected(
        product_type=ProductType.MORTGAGE,
        confidence=Confidence(0.95),
        entities=ExtractedEntities(
            amount="250,000,000 COP",
            term="20 años",
            location="Bogotá",
            additional={"income": "8,000,000 COP/mes"},
        ),
        summary="Cliente busca crédito hipotecario en Bogotá",
    )


class TestGenerateCreditApplication:
    @pytest.mark.asyncio
    async def test_generate_from_conversation_and_intent(self):
        repo = FakeApplicationRepository()
        generator = FakeApplicationGenerator()
        use_case = GenerateCreditApplication(repository=repo, generator=generator)

        app = await use_case.execute(advisor_id=ADVISOR_ID, conversation=_conversation(), intent=_intent())

        assert app.status == ApplicationStatus.DRAFT
        assert app.applicant.full_name == "María García"
        assert app.product_request.product_type == ProductType.MORTGAGE
        assert app.product_request.amount == "250,000,000 COP"
        assert app.conversation_summary == "Cliente busca crédito hipotecario en Bogotá"

    @pytest.mark.asyncio
    async def test_generated_application_is_persisted(self):
        repo = FakeApplicationRepository()
        generator = FakeApplicationGenerator()
        use_case = GenerateCreditApplication(repository=repo, generator=generator)

        app = await use_case.execute(advisor_id=ADVISOR_ID, conversation=_conversation(), intent=_intent())
        found = await repo.find_by_id(app.id)

        assert found is not None
        assert found.id == app.id

    @pytest.mark.asyncio
    async def test_rejects_non_actionable_intent(self):
        repo = FakeApplicationRepository()
        generator = FakeApplicationGenerator()
        use_case = GenerateCreditApplication(repository=repo, generator=generator)

        low_confidence_intent = IntentResult.detected(
            product_type=ProductType.MORTGAGE,
            confidence=Confidence(0.3),
            entities=ExtractedEntities(),
            summary="Mención vaga",
        )

        with pytest.raises(ApplicationGenerationError, match="actionable"):
            await use_case.execute(advisor_id=ADVISOR_ID, conversation=_conversation(), intent=low_confidence_intent)

    @pytest.mark.asyncio
    async def test_rejects_no_intent(self):
        repo = FakeApplicationRepository()
        generator = FakeApplicationGenerator()
        use_case = GenerateCreditApplication(repository=repo, generator=generator)

        no_intent = IntentResult.not_detected(summary="Chat casual")

        with pytest.raises(ApplicationGenerationError, match="actionable"):
            await use_case.execute(advisor_id=ADVISOR_ID, conversation=_conversation(), intent=no_intent)

    @pytest.mark.asyncio
    async def test_propagates_generator_error(self):
        repo = FakeApplicationRepository()
        generator = FailingApplicationGenerator()
        use_case = GenerateCreditApplication(repository=repo, generator=generator)

        with pytest.raises(ApplicationGenerationError, match="generar"):
            await use_case.execute(advisor_id=ADVISOR_ID, conversation=_conversation(), intent=_intent())


class TestGetCreditApplication:
    @pytest.mark.asyncio
    async def test_get_existing(self):
        repo = FakeApplicationRepository()
        app = CreditApplication.create(
            advisor_id=ADVISOR_ID,
            applicant=ApplicantData(full_name="María"),
            product_request=ProductRequest(product_type=ProductType.MORTGAGE),
            conversation_summary="Test",
        )
        await repo.save(app)

        use_case = GetCreditApplication(repository=repo)
        found = await use_case.execute(app.id)

        assert found is not None
        assert found.id == app.id

    @pytest.mark.asyncio
    async def test_get_nonexistent_returns_none(self):
        repo = FakeApplicationRepository()
        use_case = GetCreditApplication(repository=repo)
        result = await use_case.execute(ApplicationId.generate())
        assert result is None

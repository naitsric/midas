from uuid import UUID

from fastapi import APIRouter, HTTPException

from src.advisor.domain.entities import Advisor
from src.advisor.infrastructure.auth import RequireAdvisor
from src.application.application.use_cases import GenerateCreditApplication, ListCreditApplications
from src.application.domain.entities import CreditApplication
from src.application.domain.exceptions import ApplicationGenerationError
from src.application.domain.ports import ApplicationGenerator, ApplicationRepository
from src.application.domain.value_objects import ApplicationId
from src.application.infrastructure.schemas import (
    ApplicantResponse,
    CreditApplicationResponse,
    ProductRequestResponse,
)
from src.conversation.domain.ports import ConversationRepository
from src.conversation.domain.value_objects import ConversationId
from src.intent.application.use_cases import DetectFinancialIntent
from src.intent.domain.exceptions import IntentDetectionError
from src.intent.domain.ports import IntentDetector


def create_application_router(
    conversation_repo: ConversationRepository,
    intent_detector: IntentDetector,
    application_repo: ApplicationRepository,
    application_generator: ApplicationGenerator,
) -> APIRouter:
    router = APIRouter(prefix="/api", tags=["applications"])

    detect_intent = DetectFinancialIntent(intent_detector)
    generate_application = GenerateCreditApplication(
        repository=application_repo,
        generator=application_generator,
    )
    list_applications = ListCreditApplications(repository=application_repo)

    def _to_response(app: CreditApplication) -> CreditApplicationResponse:
        return CreditApplicationResponse(
            id=str(app.id),
            status=app.status.value,
            status_label=app.status.label,
            applicant=ApplicantResponse(
                full_name=app.applicant.full_name,
                phone=app.applicant.phone,
                estimated_income=app.applicant.estimated_income,
                employment_type=app.applicant.employment_type,
                completeness=app.applicant.completeness,
            ),
            product_request=ProductRequestResponse(
                product_type=app.product_request.product_type.value,
                product_label=app.product_request.product_type.label,
                amount=app.product_request.amount,
                term=app.product_request.term,
                location=app.product_request.location,
                summary=app.product_request.summary,
            ),
            conversation_summary=app.conversation_summary,
            rejection_reason=app.rejection_reason,
            created_at=app.created_at.isoformat(),
        )

    @router.post("/conversations/{conversation_id}/generate-application", status_code=201)
    async def generate_app(conversation_id: UUID, advisor: Advisor = RequireAdvisor) -> CreditApplicationResponse:
        conv = await conversation_repo.find_by_id_and_advisor(ConversationId(conversation_id), advisor.id)
        if conv is None:
            raise HTTPException(status_code=404, detail="Conversación no encontrada")

        try:
            intent = await detect_intent.execute(conv)
        except IntentDetectionError as e:
            raise HTTPException(status_code=422, detail=str(e)) from e

        try:
            app = await generate_application.execute(advisor_id=advisor.id, conversation=conv, intent=intent)
        except ApplicationGenerationError as e:
            raise HTTPException(status_code=422, detail=str(e)) from e

        return _to_response(app)

    @router.get("/applications")
    async def list_apps(advisor: Advisor = RequireAdvisor) -> list[CreditApplicationResponse]:
        apps = await list_applications.execute(advisor.id)
        return [_to_response(app) for app in apps]

    @router.get("/applications/{application_id}")
    async def get_app(application_id: UUID, advisor: Advisor = RequireAdvisor) -> CreditApplicationResponse:
        app = await application_repo.find_by_id_and_advisor(ApplicationId(application_id), advisor.id)
        if app is None:
            raise HTTPException(status_code=404, detail="Solicitud no encontrada")
        return _to_response(app)

    return router

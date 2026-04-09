import os

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.advisor.application.use_cases import AuthenticateAdvisor
from src.advisor.domain.ports import AdvisorRepository
from src.advisor.infrastructure.adapters import InMemoryAdvisorRepository
from src.advisor.infrastructure.api import create_advisor_router
from src.advisor.infrastructure.auth import set_authenticate_use_case
from src.application.domain.ports import ApplicationGenerator, ApplicationRepository
from src.application.infrastructure.adapters import InMemoryApplicationRepository
from src.application.infrastructure.api import create_application_router
from src.application.infrastructure.gemini_adapter import GeminiApplicationGenerator
from src.conversation.domain.ports import ConversationRepository
from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.conversation.infrastructure.api import create_conversation_router
from src.intent.domain.ports import IntentDetector
from src.intent.infrastructure.api import create_intent_router
from src.intent.infrastructure.gemini_adapter import GeminiIntentDetector


def create_app(
    conversation_repo: ConversationRepository | None = None,
    intent_detector: IntentDetector | None = None,
    application_repo: ApplicationRepository | None = None,
    application_generator: ApplicationGenerator | None = None,
    advisor_repo: AdvisorRepository | None = None,
) -> FastAPI:
    app = FastAPI(title="MIDAS Conversation Intelligence API")

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    if conversation_repo is None:
        conversation_repo = InMemoryConversationRepository()

    if intent_detector is None or application_generator is None:
        load_dotenv()
        api_key = os.getenv("GEMINI_API_KEY", "")
        if intent_detector is None:
            intent_detector = GeminiIntentDetector(api_key=api_key)
        if application_generator is None:
            application_generator = GeminiApplicationGenerator(api_key=api_key)

    if application_repo is None:
        application_repo = InMemoryApplicationRepository()

    if advisor_repo is None:
        advisor_repo = InMemoryAdvisorRepository()

    # Configurar auth
    set_authenticate_use_case(AuthenticateAdvisor(advisor_repo))

    app.include_router(create_advisor_router(advisor_repo))
    app.include_router(create_conversation_router(conversation_repo))
    app.include_router(create_intent_router(conversation_repo, intent_detector))
    app.include_router(
        create_application_router(conversation_repo, intent_detector, application_repo, application_generator)
    )

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    return app


app = create_app()

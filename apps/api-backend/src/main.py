import os
from contextlib import asynccontextmanager

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
from src.shared.infrastructure.database import Database


def create_app(
    conversation_repo: ConversationRepository | None = None,
    intent_detector: IntentDetector | None = None,
    application_repo: ApplicationRepository | None = None,
    application_generator: ApplicationGenerator | None = None,
    advisor_repo: AdvisorRepository | None = None,
    database: Database | None = None,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if database is not None:
            await database.connect()
        yield
        if database is not None:
            await database.disconnect()

    app = FastAPI(title="MIDAS Conversation Intelligence API", lifespan=lifespan)

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Si hay DATABASE_URL y no se pasaron repos explícitos, usar PostgreSQL
    load_dotenv()
    database_url = os.getenv("DATABASE_URL")

    # Solo crear Database si no se pasó ningún repo explícito (ej: en tests se pasan fakes)
    any_repo_passed = any([conversation_repo, intent_detector, application_repo, application_generator, advisor_repo])
    if database_url and database is None and not any_repo_passed:
        database = Database(database_url)

    if database is not None:
        from src.advisor.infrastructure.postgres_adapter import PostgresAdvisorRepository
        from src.application.infrastructure.postgres_adapter import PostgresApplicationRepository
        from src.conversation.infrastructure.postgres_adapter import PostgresConversationRepository

        if advisor_repo is None:
            advisor_repo = PostgresAdvisorRepository(database)
        if conversation_repo is None:
            conversation_repo = PostgresConversationRepository(database)
        if application_repo is None:
            application_repo = PostgresApplicationRepository(database)
    else:
        if conversation_repo is None:
            conversation_repo = InMemoryConversationRepository()
        if application_repo is None:
            application_repo = InMemoryApplicationRepository()
        if advisor_repo is None:
            advisor_repo = InMemoryAdvisorRepository()

    if intent_detector is None or application_generator is None:
        api_key = os.getenv("GEMINI_API_KEY", "")
        if intent_detector is None:
            intent_detector = GeminiIntentDetector(api_key=api_key)
        if application_generator is None:
            application_generator = GeminiApplicationGenerator(api_key=api_key)

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

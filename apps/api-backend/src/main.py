import os
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.advisor.application.use_cases import AuthenticateAdvisor
from src.advisor.domain.ports import AdvisorRepository, VoipPushRegistrar
from src.advisor.infrastructure.adapters import InMemoryAdvisorRepository
from src.advisor.infrastructure.api import create_advisor_router
from src.advisor.infrastructure.auth import set_authenticate_use_case
from src.application.domain.ports import ApplicationGenerator, ApplicationRepository
from src.application.infrastructure.adapters import InMemoryApplicationRepository
from src.application.infrastructure.api import create_application_router
from src.application.infrastructure.gemini_adapter import GeminiApplicationGenerator
from src.call.domain.ports import CallRepository, PhoneNumberRepository
from src.call.infrastructure.adapters import InMemoryCallRepository, InMemoryPhoneNumberRepository
from src.call.infrastructure.api import create_call_router
from src.conversation.domain.ports import ConversationRepository
from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.conversation.infrastructure.api import create_conversation_router
from src.copilot.domain.ports import CopilotAgent
from src.copilot.infrastructure.adk.agent import AdkCopilotAgent
from src.copilot.infrastructure.api import create_copilot_router
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
    call_repo: CallRepository | None = None,
    phone_repo: PhoneNumberRepository | None = None,
    voip_registrar: VoipPushRegistrar | None = None,
    copilot_agent: CopilotAgent | None = None,
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

    # Configurar ADK (google.genai) para usar AI Studio con nuestra GEMINI_API_KEY.
    # Por default google.genai va a Vertex AI, que requiere un project GCP distinto.
    gemini_api_key = os.getenv("GEMINI_API_KEY", "")
    if gemini_api_key and not os.getenv("GOOGLE_API_KEY"):
        os.environ["GOOGLE_API_KEY"] = gemini_api_key
    os.environ.setdefault("GOOGLE_GENAI_USE_VERTEXAI", "0")

    # Solo crear Database si no se pasó ningún repo explícito (ej: en tests se pasan fakes)
    any_repo_passed = any(
        [conversation_repo, intent_detector, application_repo, application_generator,
         advisor_repo, call_repo, phone_repo]
    )
    if database_url and database is None and not any_repo_passed:
        database = Database(database_url)

    if database is not None:
        from src.advisor.infrastructure.postgres_adapter import PostgresAdvisorRepository
        from src.application.infrastructure.postgres_adapter import PostgresApplicationRepository
        from src.call.infrastructure.postgres_adapter import PostgresCallRepository, PostgresPhoneNumberRepository
        from src.conversation.infrastructure.postgres_adapter import PostgresConversationRepository

        if advisor_repo is None:
            advisor_repo = PostgresAdvisorRepository(database)
        if conversation_repo is None:
            conversation_repo = PostgresConversationRepository(database)
        if application_repo is None:
            application_repo = PostgresApplicationRepository(database)
        if call_repo is None:
            call_repo = PostgresCallRepository(database)
        if phone_repo is None:
            phone_repo = PostgresPhoneNumberRepository(database)
    else:
        if conversation_repo is None:
            conversation_repo = InMemoryConversationRepository()
        if application_repo is None:
            application_repo = InMemoryApplicationRepository()
        if advisor_repo is None:
            advisor_repo = InMemoryAdvisorRepository()

    if call_repo is None:
        call_repo = InMemoryCallRepository()
    if phone_repo is None:
        phone_repo = InMemoryPhoneNumberRepository()

    if intent_detector is None or application_generator is None:
        api_key = os.getenv("GEMINI_API_KEY", "")
        if intent_detector is None:
            intent_detector = GeminiIntentDetector(api_key=api_key)
        if application_generator is None:
            application_generator = GeminiApplicationGenerator(api_key=api_key)

    # Configurar auth
    auth_use_case = AuthenticateAdvisor(advisor_repo)
    set_authenticate_use_case(auth_use_case)

    # VoIP push registrar (SNS APNS_VOIP). Se inicializa lazy si está la SSM param.
    if voip_registrar is None and os.getenv("SNS_VOIP_PLATFORM_APP_ARN_PARAM"):
        try:
            from src.advisor.infrastructure.sns_adapter import SnsVoipPushRegistrar

            voip_registrar = SnsVoipPushRegistrar()
        except Exception as e:
            print(f"WARN: no se pudo inicializar SnsVoipPushRegistrar: {e}")
            voip_registrar = None

    # Speech-to-Text transcriber
    speech_transcriber = None
    google_project = os.getenv("GOOGLE_CLOUD_PROJECT")
    if google_project:
        from src.call.infrastructure.google_stt_adapter import GoogleSpeechTranscriber

        speech_transcriber = GoogleSpeechTranscriber(project_id=google_project)
    else:
        gemini_key = os.getenv("GEMINI_API_KEY", "")
        if gemini_key:
            from src.call.infrastructure.google_stt_adapter import GeminiSpeechTranscriber

            speech_transcriber = GeminiSpeechTranscriber(api_key=gemini_key)

    app.include_router(create_advisor_router(advisor_repo, voip_registrar=voip_registrar))
    app.include_router(create_conversation_router(conversation_repo))
    app.include_router(create_intent_router(conversation_repo, intent_detector))
    app.include_router(
        create_application_router(conversation_repo, intent_detector, application_repo, application_generator)
    )
    app.include_router(
        create_call_router(
            call_repo,
            transcriber=speech_transcriber,
            authenticate=auth_use_case,
            intent_detector=intent_detector,
            application_repo=application_repo,
            application_generator=application_generator,
            phone_repo=phone_repo,
            advisor_repo=advisor_repo,
        )
    )

    # Copilot — ADK-based assistant. Reusa los mismos repos para sus tools.
    if copilot_agent is None:
        copilot_agent = AdkCopilotAgent(
            call_repo=call_repo,
            application_repo=application_repo,
            conversation_repo=conversation_repo,
        )
    app.include_router(create_copilot_router(copilot_agent))

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    return app


app = create_app()

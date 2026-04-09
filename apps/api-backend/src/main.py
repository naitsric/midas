import os

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.conversation.domain.ports import ConversationRepository
from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.conversation.infrastructure.api import create_conversation_router
from src.intent.domain.ports import IntentDetector
from src.intent.infrastructure.api import create_intent_router
from src.intent.infrastructure.gemini_adapter import GeminiIntentDetector


def create_app(
    conversation_repo: ConversationRepository | None = None,
    intent_detector: IntentDetector | None = None,
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

    if intent_detector is None:
        load_dotenv()
        api_key = os.getenv("GEMINI_API_KEY", "")
        intent_detector = GeminiIntentDetector(api_key=api_key)

    app.include_router(create_conversation_router(conversation_repo))
    app.include_router(create_intent_router(conversation_repo, intent_detector))

    @app.get("/health")
    async def health():
        return {"status": "ok"}

    return app


app = create_app()

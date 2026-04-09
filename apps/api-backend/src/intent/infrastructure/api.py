from uuid import UUID

from fastapi import APIRouter, HTTPException

from src.conversation.application.use_cases import GetConversation
from src.conversation.domain.ports import ConversationRepository
from src.conversation.domain.value_objects import ConversationId
from src.intent.application.use_cases import DetectFinancialIntent
from src.intent.domain.exceptions import IntentDetectionError
from src.intent.domain.ports import IntentDetector
from src.intent.infrastructure.schemas import IntentResponse


def create_intent_router(repository: ConversationRepository, detector: IntentDetector) -> APIRouter:
    router = APIRouter(prefix="/api/conversations", tags=["intent"])
    get_conversation = GetConversation(repository)
    detect_intent = DetectFinancialIntent(detector)

    @router.post("/{conversation_id}/detect-intent")
    async def detect_conversation_intent(conversation_id: UUID) -> IntentResponse:
        conv = await get_conversation.execute(ConversationId(conversation_id))
        if conv is None:
            raise HTTPException(status_code=404, detail="Conversación no encontrada")

        try:
            result = await detect_intent.execute(conv)
        except IntentDetectionError as e:
            raise HTTPException(status_code=422, detail=str(e)) from e

        return IntentResponse(
            intent_detected=result.intent_detected,
            confidence=result.confidence.value,
            product_type=result.product_type.value if result.product_type else None,
            entities={
                "amount": result.entities.amount,
                "term": result.entities.term,
                "location": result.entities.location,
                **result.entities.additional,
            },
            summary=result.summary,
            is_actionable=result.is_actionable,
        )

    return router

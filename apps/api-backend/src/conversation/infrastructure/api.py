from uuid import UUID

from fastapi import APIRouter, HTTPException

from src.advisor.domain.entities import Advisor
from src.advisor.infrastructure.auth import RequireAdvisor
from src.conversation.application.use_cases import SaveConversation
from src.conversation.domain.entities import Conversation
from src.conversation.domain.ports import ConversationRepository
from src.conversation.domain.value_objects import ConversationId, MessageSender
from src.conversation.infrastructure.schemas import (
    AddMessageRequest,
    ConversationResponse,
    CreateConversationRequest,
    MessageResponse,
)


def create_conversation_router(repository: ConversationRepository) -> APIRouter:
    router = APIRouter(prefix="/api/conversations", tags=["conversations"])
    save_use_case = SaveConversation(repository)

    def _to_response(conv: Conversation) -> ConversationResponse:
        return ConversationResponse(
            id=str(conv.id),
            advisor_name=conv.advisor_name,
            client_name=conv.client_name,
            message_count=conv.message_count,
            messages=[
                MessageResponse(
                    sender_name=m.sender.name,
                    is_advisor=m.sender.is_advisor,
                    text=m.text,
                    timestamp=m.timestamp.isoformat(),
                )
                for m in conv.messages
            ],
            created_at=conv.created_at.isoformat(),
        )

    async def _get_conversation_for_advisor(conversation_id: UUID, advisor: Advisor) -> Conversation:
        conv = await repository.find_by_id_and_advisor(ConversationId(conversation_id), advisor.id)
        if conv is None:
            raise HTTPException(status_code=404, detail="Conversación no encontrada")
        return conv

    @router.post("", status_code=201)
    async def create_conversation(
        request: CreateConversationRequest, advisor: Advisor = RequireAdvisor
    ) -> ConversationResponse:
        conv = Conversation.create(
            advisor_id=advisor.id,
            advisor_name=request.advisor_name,
            client_name=request.client_name,
        )
        await save_use_case.execute(conv)
        return _to_response(conv)

    @router.get("/{conversation_id}")
    async def get_conversation(conversation_id: UUID, advisor: Advisor = RequireAdvisor) -> ConversationResponse:
        conv = await _get_conversation_for_advisor(conversation_id, advisor)
        return _to_response(conv)

    @router.post("/{conversation_id}/messages", status_code=201)
    async def add_message(
        conversation_id: UUID, request: AddMessageRequest, advisor: Advisor = RequireAdvisor
    ) -> ConversationResponse:
        conv = await _get_conversation_for_advisor(conversation_id, advisor)

        sender = (
            MessageSender.advisor(request.sender_name)
            if request.is_advisor
            else MessageSender.client(request.sender_name)
        )
        conv.add_message(sender=sender, text=request.text)
        await save_use_case.execute(conv)
        return _to_response(conv)

    return router

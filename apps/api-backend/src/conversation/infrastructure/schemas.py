from pydantic import BaseModel


class CreateConversationRequest(BaseModel):
    advisor_name: str
    client_name: str


class AddMessageRequest(BaseModel):
    sender_name: str
    is_advisor: bool
    text: str


class ImportMessageRequest(BaseModel):
    sender_name: str
    is_advisor: bool
    text: str


class ImportConversationRequest(BaseModel):
    advisor_name: str
    client_name: str
    messages: list[ImportMessageRequest]


class MessageResponse(BaseModel):
    sender_name: str
    is_advisor: bool
    text: str
    timestamp: str


class ConversationSummaryResponse(BaseModel):
    id: str
    advisor_name: str
    client_name: str
    message_count: int
    created_at: str


class ConversationResponse(BaseModel):
    id: str
    advisor_name: str
    client_name: str
    message_count: int
    messages: list[MessageResponse]
    created_at: str

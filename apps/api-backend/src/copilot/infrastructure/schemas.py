from pydantic import BaseModel, Field


class CopilotHistoryItem(BaseModel):
    role: str = Field(description="'user' o 'assistant'")
    text: str


class CopilotMessageRequest(BaseModel):
    history: list[CopilotHistoryItem] = Field(default_factory=list)
    message: str

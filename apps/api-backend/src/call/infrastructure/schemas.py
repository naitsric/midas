from pydantic import BaseModel


class StartCallRequest(BaseModel):
    client_name: str


class CallResponse(BaseModel):
    id: str
    client_name: str
    status: str
    transcript: str
    duration_seconds: int | None
    created_at: str
    completed_at: str | None


class CallSummaryResponse(BaseModel):
    id: str
    client_name: str
    status: str
    duration_seconds: int | None
    created_at: str

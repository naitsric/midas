from pydantic import BaseModel, Field


class StartCallRequest(BaseModel):
    client_name: str
    voip_call_id: str | None = Field(default=None, alias="voipCallId")
    model_config = {"populate_by_name": True}


class CallResponse(BaseModel):
    id: str
    client_name: str
    status: str
    transcript: str
    duration_seconds: int | None
    voip_call_id: str | None = None
    recording_url: str | None = None
    created_at: str
    completed_at: str | None


class CallSummaryResponse(BaseModel):
    id: str
    client_name: str
    status: str
    duration_seconds: int | None
    created_at: str


class VoipWebhookRequest(BaseModel):
    model_config = {"populate_by_name": True}

    event: str
    call_id: str = Field(alias="callId")
    timestamp: str


class VoipRecordingRequest(BaseModel):
    model_config = {"populate_by_name": True}

    call_id: str = Field(alias="callId")
    recording_url: str = Field(alias="recordingUrl")
    bucket: str
    key: str
    size: int
    duration_seconds: int = Field(alias="durationSeconds")
    content_type: str = Field(default="audio/wav", alias="contentType")
    timestamp: str

from pydantic import BaseModel


class RegisterAdvisorRequest(BaseModel):
    name: str
    email: str
    phone: str


class AdvisorResponse(BaseModel):
    id: str
    name: str
    email: str
    phone: str
    status: str
    status_label: str
    api_key_masked: str
    created_at: str


class AdvisorRegisteredResponse(AdvisorResponse):
    api_key: str


class RegisterVoipTokenRequest(BaseModel):
    device_token: str


class VoipTokenRegisteredResponse(BaseModel):
    advisor_id: str
    voip_endpoint_arn: str

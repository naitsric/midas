from pydantic import BaseModel


class ApplicantResponse(BaseModel):
    full_name: str
    phone: str | None
    estimated_income: str | None
    employment_type: str | None
    completeness: float


class ProductRequestResponse(BaseModel):
    product_type: str
    product_label: str
    amount: str | None
    term: str | None
    location: str | None
    summary: str


class CreditApplicationResponse(BaseModel):
    id: str
    status: str
    status_label: str
    applicant: ApplicantResponse
    product_request: ProductRequestResponse
    conversation_summary: str
    rejection_reason: str | None
    created_at: str

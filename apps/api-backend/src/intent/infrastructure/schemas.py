from pydantic import BaseModel


class IntentResponse(BaseModel):
    intent_detected: bool
    confidence: float
    product_type: str | None
    entities: dict
    summary: str
    is_actionable: bool

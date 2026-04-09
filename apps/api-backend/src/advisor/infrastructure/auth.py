from fastapi import Depends, HTTPException, Security
from fastapi.security import APIKeyHeader

from src.advisor.application.use_cases import AuthenticateAdvisor
from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import AdvisorAuthenticationError
from src.advisor.domain.value_objects import ApiKey

api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)

# Se inyecta desde create_app
_authenticate_use_case: AuthenticateAdvisor | None = None


def set_authenticate_use_case(use_case: AuthenticateAdvisor) -> None:
    global _authenticate_use_case  # noqa: PLW0603
    _authenticate_use_case = use_case


async def get_current_advisor(api_key: str | None = Security(api_key_header)) -> Advisor:
    if _authenticate_use_case is None:
        raise HTTPException(status_code=500, detail="Auth no configurado")
    if api_key is None:
        raise HTTPException(status_code=401, detail="API key requerida (header X-API-Key)")

    try:
        return await _authenticate_use_case.execute(ApiKey(api_key))
    except AdvisorAuthenticationError as e:
        raise HTTPException(status_code=401, detail=str(e)) from e


RequireAdvisor = Depends(get_current_advisor)

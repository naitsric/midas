from fastapi import APIRouter, HTTPException

from src.advisor.application.use_cases import RegisterAdvisor, RegisterVoipDeviceToken
from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import InvalidAdvisorError
from src.advisor.domain.ports import AdvisorRepository, VoipPushRegistrar
from src.advisor.infrastructure.auth import RequireAdvisor
from src.advisor.infrastructure.schemas import (
    AdvisorRegisteredResponse,
    AdvisorResponse,
    RegisterAdvisorRequest,
    RegisterVoipTokenRequest,
    VoipTokenRegisteredResponse,
)


def _to_response(advisor: Advisor) -> AdvisorResponse:
    return AdvisorResponse(
        id=str(advisor.id),
        name=advisor.name,
        email=advisor.email,
        phone=advisor.phone,
        status=advisor.status.value,
        status_label=advisor.status.label,
        api_key_masked=advisor.api_key.masked,
        created_at=advisor.created_at.isoformat(),
    )


def create_advisor_router(
    repository: AdvisorRepository,
    voip_registrar: VoipPushRegistrar | None = None,
) -> APIRouter:
    router = APIRouter(prefix="/api/advisors", tags=["advisors"])
    register_use_case = RegisterAdvisor(repository)
    register_voip_token = (
        RegisterVoipDeviceToken(repository, voip_registrar) if voip_registrar else None
    )

    @router.post("", status_code=201)
    async def register_advisor(request: RegisterAdvisorRequest) -> AdvisorRegisteredResponse:
        try:
            advisor = await register_use_case.execute(
                name=request.name,
                email=request.email,
                phone=request.phone,
            )
        except InvalidAdvisorError as e:
            raise HTTPException(status_code=422, detail=str(e)) from e

        base = _to_response(advisor)
        return AdvisorRegisteredResponse(
            **base.model_dump(),
            api_key=advisor.api_key.value,
        )

    @router.get("/me")
    async def get_current_advisor(advisor: Advisor = RequireAdvisor) -> AdvisorResponse:
        return _to_response(advisor)

    @router.post("/me/voip-token", status_code=200)
    async def register_voip_token_endpoint(
        request: RegisterVoipTokenRequest,
        advisor: Advisor = RequireAdvisor,
    ) -> VoipTokenRegisteredResponse:
        if register_voip_token is None:
            raise HTTPException(status_code=503, detail="VoIP push registrar no disponible")
        try:
            updated = await register_voip_token.execute(advisor, request.device_token)
        except InvalidAdvisorError as e:
            raise HTTPException(status_code=422, detail=str(e)) from e
        return VoipTokenRegisteredResponse(
            advisor_id=str(updated.id),
            voip_endpoint_arn=updated.voip_endpoint_arn or "",
        )

    return router

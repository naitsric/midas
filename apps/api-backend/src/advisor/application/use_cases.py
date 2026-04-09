from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import AdvisorAuthenticationError, InvalidAdvisorError
from src.advisor.domain.ports import AdvisorRepository
from src.advisor.domain.value_objects import AdvisorId, ApiKey


class RegisterAdvisor:
    def __init__(self, repository: AdvisorRepository):
        self._repository = repository

    async def execute(self, name: str, email: str, phone: str) -> Advisor:
        existing = await self._repository.find_by_email(email)
        if existing is not None:
            raise InvalidAdvisorError(f"Ya existe un asesor con el email {email}")

        advisor = Advisor.register(name=name, email=email, phone=phone)
        await self._repository.save(advisor)
        return advisor


class GetAdvisor:
    def __init__(self, repository: AdvisorRepository):
        self._repository = repository

    async def execute(self, advisor_id: AdvisorId) -> Advisor | None:
        return await self._repository.find_by_id(advisor_id)


class AuthenticateAdvisor:
    def __init__(self, repository: AdvisorRepository):
        self._repository = repository

    async def execute(self, api_key: ApiKey) -> Advisor:
        advisor = await self._repository.find_by_api_key(api_key)
        if advisor is None:
            raise AdvisorAuthenticationError("API key inválida")
        if not advisor.is_active:
            raise AdvisorAuthenticationError("El asesor no está activo")
        return advisor

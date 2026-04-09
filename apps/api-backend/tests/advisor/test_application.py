import pytest

from src.advisor.application.use_cases import AuthenticateAdvisor, GetAdvisor, RegisterAdvisor
from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import AdvisorAuthenticationError, InvalidAdvisorError
from src.advisor.domain.ports import AdvisorRepository
from src.advisor.domain.value_objects import AdvisorId, AdvisorStatus, ApiKey


class InMemoryAdvisorRepository(AdvisorRepository):
    def __init__(self):
        self._store: dict[AdvisorId, Advisor] = {}

    async def save(self, advisor: Advisor) -> None:
        self._store[advisor.id] = advisor

    async def find_by_id(self, advisor_id: AdvisorId) -> Advisor | None:
        return self._store.get(advisor_id)

    async def find_by_api_key(self, api_key: ApiKey) -> Advisor | None:
        return next((a for a in self._store.values() if a.api_key == api_key), None)

    async def find_by_email(self, email: str) -> Advisor | None:
        return next((a for a in self._store.values() if a.email == email), None)


@pytest.fixture
def repo():
    return InMemoryAdvisorRepository()


class TestRegisterAdvisor:
    @pytest.mark.asyncio
    async def test_register_new_advisor(self, repo):
        use_case = RegisterAdvisor(repo)
        advisor = await use_case.execute(name="Carlos Pérez", email="carlos@example.com", phone="3001234567")

        assert advisor.name == "Carlos Pérez"
        assert advisor.email == "carlos@example.com"
        assert advisor.status == AdvisorStatus.ACTIVE
        assert advisor.api_key is not None

    @pytest.mark.asyncio
    async def test_register_persists(self, repo):
        use_case = RegisterAdvisor(repo)
        advisor = await use_case.execute(name="Carlos", email="c@e.com", phone="300")

        found = await repo.find_by_id(advisor.id)
        assert found is not None
        assert found.id == advisor.id

    @pytest.mark.asyncio
    async def test_register_duplicate_email_raises(self, repo):
        use_case = RegisterAdvisor(repo)
        await use_case.execute(name="Carlos", email="carlos@example.com", phone="300")

        with pytest.raises(InvalidAdvisorError, match="email"):
            await use_case.execute(name="Otro Carlos", email="carlos@example.com", phone="301")


class TestGetAdvisor:
    @pytest.mark.asyncio
    async def test_get_existing(self, repo):
        register = RegisterAdvisor(repo)
        advisor = await register.execute(name="Carlos", email="c@e.com", phone="300")

        use_case = GetAdvisor(repo)
        found = await use_case.execute(advisor.id)

        assert found is not None
        assert found.name == "Carlos"

    @pytest.mark.asyncio
    async def test_get_nonexistent_returns_none(self, repo):
        use_case = GetAdvisor(repo)
        result = await use_case.execute(AdvisorId.generate())
        assert result is None


class TestAuthenticateAdvisor:
    @pytest.mark.asyncio
    async def test_authenticate_valid_key(self, repo):
        register = RegisterAdvisor(repo)
        advisor = await register.execute(name="Carlos", email="c@e.com", phone="300")

        use_case = AuthenticateAdvisor(repo)
        authenticated = await use_case.execute(advisor.api_key)

        assert authenticated.id == advisor.id

    @pytest.mark.asyncio
    async def test_authenticate_invalid_key_raises(self, repo):
        use_case = AuthenticateAdvisor(repo)

        with pytest.raises(AdvisorAuthenticationError, match="inválida"):
            await use_case.execute(ApiKey("midas_fake_key_12345"))

    @pytest.mark.asyncio
    async def test_authenticate_suspended_raises(self, repo):
        register = RegisterAdvisor(repo)
        advisor = await register.execute(name="Carlos", email="c@e.com", phone="300")
        advisor.suspend(reason="Test")
        await repo.save(advisor)

        use_case = AuthenticateAdvisor(repo)

        with pytest.raises(AdvisorAuthenticationError, match="activ"):
            await use_case.execute(advisor.api_key)

    @pytest.mark.asyncio
    async def test_authenticate_deactivated_raises(self, repo):
        register = RegisterAdvisor(repo)
        advisor = await register.execute(name="Carlos", email="c@e.com", phone="300")
        advisor.deactivate()
        await repo.save(advisor)

        use_case = AuthenticateAdvisor(repo)

        with pytest.raises(AdvisorAuthenticationError, match="activ"):
            await use_case.execute(advisor.api_key)

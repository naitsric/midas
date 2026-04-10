import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.call.application.use_cases import EndCall, GetCall, ListCalls, StartCall
from src.call.domain.value_objects import CallId, CallStatus
from src.call.infrastructure.adapters import InMemoryCallRepository


@pytest.fixture
def repository():
    return InMemoryCallRepository()


class TestStartCall:
    @pytest.mark.asyncio
    async def test_start_call(self, repository):
        use_case = StartCall(repository)
        advisor_id = AdvisorId.generate()

        call = await use_case.execute(advisor_id=advisor_id, client_name="María García")

        assert call.client_name == "María García"
        assert call.status == CallStatus.RECORDING
        assert call.advisor_id == advisor_id

        # Verificar que se guardó
        saved = await repository.find_by_id(call.id)
        assert saved is not None
        assert saved.id == call.id


class TestEndCall:
    @pytest.mark.asyncio
    async def test_end_call(self, repository):
        start = StartCall(repository)
        end = EndCall(repository)
        call = await start.execute(advisor_id=AdvisorId.generate(), client_name="Cliente")

        call.append_transcript("Busco crédito hipotecario.")
        result = await end.execute(call, duration_seconds=180)

        assert result.status == CallStatus.COMPLETED
        assert result.duration_seconds == 180
        assert result.transcript == "Busco crédito hipotecario."


class TestListCalls:
    @pytest.mark.asyncio
    async def test_list_empty(self, repository):
        use_case = ListCalls(repository)
        result = await use_case.execute(AdvisorId.generate())
        assert result == []

    @pytest.mark.asyncio
    async def test_list_by_advisor(self, repository):
        start = StartCall(repository)
        advisor_a = AdvisorId.generate()
        advisor_b = AdvisorId.generate()

        await start.execute(advisor_id=advisor_a, client_name="Cliente A")
        await start.execute(advisor_id=advisor_a, client_name="Cliente B")
        await start.execute(advisor_id=advisor_b, client_name="Cliente C")

        list_calls = ListCalls(repository)
        result = await list_calls.execute(advisor_a)
        assert len(result) == 2


class TestGetCall:
    @pytest.mark.asyncio
    async def test_get_existing(self, repository):
        start = StartCall(repository)
        call = await start.execute(advisor_id=AdvisorId.generate(), client_name="Cliente")

        get = GetCall(repository)
        result = await get.execute(call.id)
        assert result is not None
        assert result.id == call.id

    @pytest.mark.asyncio
    async def test_get_nonexistent(self, repository):
        get = GetCall(repository)
        result = await get.execute(CallId.generate())
        assert result is None

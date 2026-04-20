from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.entities import CallRecording
from src.call.domain.ports import CallRepository
from src.call.domain.value_objects import CallId


class StartCall:
    def __init__(self, repository: CallRepository):
        self._repository = repository

    async def execute(
        self,
        advisor_id: AdvisorId,
        client_name: str,
        voip_call_id: str | None = None,
    ) -> CallRecording:
        call = CallRecording.create(advisor_id=advisor_id, client_name=client_name)
        if voip_call_id:
            call.voip_call_id = voip_call_id
        await self._repository.save(call)
        return call


class EndCall:
    def __init__(self, repository: CallRepository):
        self._repository = repository

    async def execute(self, call: CallRecording, duration_seconds: int | None = None) -> CallRecording:
        call.mark_processing()
        call.complete(duration_seconds=duration_seconds)
        await self._repository.save(call)
        return call


class ListCalls:
    def __init__(self, repository: CallRepository):
        self._repository = repository

    async def execute(self, advisor_id: AdvisorId) -> list[CallRecording]:
        return await self._repository.find_all_by_advisor(advisor_id)


class GetCall:
    def __init__(self, repository: CallRepository):
        self._repository = repository

    async def execute(self, call_id: CallId) -> CallRecording | None:
        return await self._repository.find_by_id(call_id)

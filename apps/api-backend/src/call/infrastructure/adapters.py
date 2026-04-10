from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.entities import CallRecording
from src.call.domain.ports import CallRepository
from src.call.domain.value_objects import CallId


class InMemoryCallRepository(CallRepository):
    def __init__(self):
        self._store: dict[CallId, CallRecording] = {}

    async def save(self, call: CallRecording) -> None:
        self._store[call.id] = call

    async def find_by_id(self, call_id: CallId) -> CallRecording | None:
        return self._store.get(call_id)

    async def find_by_id_and_advisor(self, call_id: CallId, advisor_id: AdvisorId) -> CallRecording | None:
        call = self._store.get(call_id)
        if call is not None and call.advisor_id == advisor_id:
            return call
        return None

    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[CallRecording]:
        return [c for c in self._store.values() if c.advisor_id == advisor_id]

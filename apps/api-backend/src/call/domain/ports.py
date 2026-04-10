from abc import ABC, abstractmethod
from collections.abc import AsyncIterator
from dataclasses import dataclass

from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.entities import CallRecording
from src.call.domain.value_objects import CallId


class CallRepository(ABC):
    @abstractmethod
    async def save(self, call: CallRecording) -> None:
        pass

    @abstractmethod
    async def find_by_id(self, call_id: CallId) -> CallRecording | None:
        pass

    @abstractmethod
    async def find_by_id_and_advisor(self, call_id: CallId, advisor_id: AdvisorId) -> CallRecording | None:
        pass

    @abstractmethod
    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[CallRecording]:
        pass


@dataclass(frozen=True)
class TranscriptChunk:
    text: str
    is_final: bool


class SpeechTranscriber(ABC):
    @abstractmethod
    async def transcribe_stream(self, audio_chunks: AsyncIterator[bytes]) -> AsyncIterator[TranscriptChunk]:
        """Recibe stream de audio PCM, emite chunks de transcripción."""
        ...

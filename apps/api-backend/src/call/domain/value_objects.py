from dataclasses import dataclass
from enum import Enum
from uuid import UUID, uuid4


@dataclass(frozen=True)
class CallId:
    value: UUID

    @classmethod
    def generate(cls) -> "CallId":
        return cls(value=uuid4())

    def __str__(self) -> str:
        return str(self.value)


class CallStatus(Enum):
    RECORDING = "recording"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"

    @property
    def label(self) -> str:
        return self.value

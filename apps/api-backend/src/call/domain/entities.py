from dataclasses import dataclass, field
from datetime import UTC, datetime

from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.exceptions import InvalidCallStateError
from src.call.domain.value_objects import CallId, CallStatus


@dataclass
class CallRecording:
    id: CallId
    advisor_id: AdvisorId
    client_name: str
    status: CallStatus = CallStatus.RECORDING
    transcript: str = ""
    duration_seconds: int | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    completed_at: datetime | None = None

    @classmethod
    def create(cls, advisor_id: AdvisorId, client_name: str) -> "CallRecording":
        if not client_name or not client_name.strip():
            raise InvalidCallStateError("El nombre del cliente no puede estar vacío")
        return cls(
            id=CallId.generate(),
            advisor_id=advisor_id,
            client_name=client_name.strip(),
        )

    def append_transcript(self, text: str) -> None:
        if self.status != CallStatus.RECORDING:
            raise InvalidCallStateError(f"No se puede agregar transcripción en estado {self.status.label}")
        if self.transcript:
            self.transcript += " " + text
        else:
            self.transcript = text

    def mark_processing(self) -> None:
        if self.status != CallStatus.RECORDING:
            raise InvalidCallStateError(f"No se puede procesar desde estado {self.status.label}")
        self.status = CallStatus.PROCESSING

    def complete(self, duration_seconds: int | None = None) -> None:
        if self.status != CallStatus.PROCESSING:
            raise InvalidCallStateError(f"No se puede completar desde estado {self.status.label}")
        self.status = CallStatus.COMPLETED
        self.duration_seconds = duration_seconds
        self.completed_at = datetime.now(UTC)

    def fail(self) -> None:
        self.status = CallStatus.FAILED
        self.completed_at = datetime.now(UTC)

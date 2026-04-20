import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.entities import CallRecording
from src.call.domain.exceptions import InvalidCallStateError
from src.call.domain.value_objects import CallStatus


class TestCallRecordingCreation:
    def test_create_call(self):
        advisor_id = AdvisorId.generate()
        call = CallRecording.create(advisor_id=advisor_id, client_name="María García")

        assert call.advisor_id == advisor_id
        assert call.client_name == "María García"
        assert call.status == CallStatus.RECORDING
        assert call.transcript == ""
        assert call.duration_seconds is None
        assert call.completed_at is None

    def test_create_call_strips_name(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="  Juan López  ")
        assert call.client_name == "Juan López"

    def test_create_call_empty_name_raises(self):
        with pytest.raises(InvalidCallStateError, match="nombre del cliente"):
            CallRecording.create(advisor_id=AdvisorId.generate(), client_name="")

    def test_create_call_blank_name_raises(self):
        with pytest.raises(InvalidCallStateError, match="nombre del cliente"):
            CallRecording.create(advisor_id=AdvisorId.generate(), client_name="   ")


class TestTranscript:
    def test_append_transcript(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.append_transcript("Hola, busco un crédito.")
        assert call.transcript == "Hola, busco un crédito."

    def test_append_transcript_concatenates(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.append_transcript("Hola.")
        call.append_transcript("Busco un crédito hipotecario.")
        assert call.transcript == "Hola. Busco un crédito hipotecario."

    def test_append_transcript_not_recording_raises(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.mark_processing()
        with pytest.raises(InvalidCallStateError):
            call.append_transcript("Texto")


class TestCallLifecycle:
    def test_recording_to_processing(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.mark_processing()
        assert call.status == CallStatus.PROCESSING

    def test_processing_to_completed(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.mark_processing()
        call.complete(duration_seconds=120)
        assert call.status == CallStatus.COMPLETED
        assert call.duration_seconds == 120
        assert call.completed_at is not None

    def test_complete_without_duration(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.mark_processing()
        call.complete()
        assert call.status == CallStatus.COMPLETED
        assert call.duration_seconds is None

    def test_cannot_complete_from_recording(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        with pytest.raises(InvalidCallStateError):
            call.complete()

    def test_cannot_process_twice(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.mark_processing()
        with pytest.raises(InvalidCallStateError):
            call.mark_processing()

    def test_fail_from_any_state(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="Cliente")
        call.fail()
        assert call.status == CallStatus.FAILED
        assert call.completed_at is not None

    def test_full_lifecycle(self):
        call = CallRecording.create(advisor_id=AdvisorId.generate(), client_name="María")
        call.append_transcript("Hola, necesito un crédito.")
        call.append_transcript("Para comprar casa en Bogotá.")
        call.mark_processing()
        call.complete(duration_seconds=300)

        assert call.status == CallStatus.COMPLETED
        assert call.transcript == "Hola, necesito un crédito. Para comprar casa en Bogotá."
        assert call.duration_seconds == 300

import pytest
from fastapi.testclient import TestClient

from src.advisor.infrastructure.adapters import InMemoryAdvisorRepository
from src.call.domain.ports import TranscriptChunk
from src.call.infrastructure.adapters import InMemoryCallRepository
from src.call.infrastructure.google_stt_adapter import FakeSpeechTranscriber
from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.intent.domain.entities import IntentResult
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType
from src.main import create_app
from tests.test_api import FakeApplicationGenerator, FakeIntentDetector


def _create_app_with_transcriber(call_repo, transcriber):
    fake_detector = FakeIntentDetector(
        IntentResult.detected(
            product_type=ProductType.MORTGAGE,
            confidence=Confidence(0.95),
            entities=ExtractedEntities(amount="250M COP"),
            summary="Busca hipoteca",
        )
    )
    return create_app(
        conversation_repo=InMemoryConversationRepository(),
        intent_detector=fake_detector,
        application_repo=None,
        application_generator=FakeApplicationGenerator(),
        call_repo=call_repo,
    )


@pytest.fixture
def call_repo():
    return InMemoryCallRepository()


@pytest.fixture
def advisor_repo():
    return InMemoryAdvisorRepository()


@pytest.fixture
def transcriber():
    return FakeSpeechTranscriber(
        chunks=[
            TranscriptChunk(text="Hola, busco un crédito.", is_final=True),
            TranscriptChunk(text="Para comprar casa.", is_final=True),
        ]
    )


@pytest.fixture
def client(call_repo, transcriber):
    app = _create_app_with_transcriber(call_repo, transcriber)
    return TestClient(app)


def _register_advisor(client: TestClient) -> tuple[str, str]:
    resp = client.post(
        "/api/advisors",
        json={"name": "Carlos", "email": "carlos@ws.com", "phone": "300111"},
    )
    data = resp.json()
    return data["api_key"], data["id"]


class TestWebSocketAuth:
    def test_ws_without_api_key_closes(self, client, call_repo):
        # Crear una llamada directamente para tener un call_id válido
        with pytest.raises(Exception):
            with client.websocket_connect("/api/calls/00000000-0000-0000-0000-000000000000/stream"):
                pass

    def test_ws_with_invalid_api_key_closes(self, client):
        with pytest.raises(Exception):
            with client.websocket_connect("/api/calls/00000000-0000-0000-0000-000000000000/stream?api_key=invalid"):
                pass


class TestWebSocketStream:
    def test_ws_end_action_completes_call(self, client, call_repo):
        api_key, _ = _register_advisor(client)

        # Crear llamada via REST
        resp = client.post(
            "/api/calls",
            json={"client_name": "María"},
            headers={"X-API-Key": api_key},
        )
        call_id = resp.json()["id"]

        # Conectar WebSocket y enviar end
        with client.websocket_connect(f"/api/calls/{call_id}/stream?api_key={api_key}") as ws:
            # El transcriber no está configurado en el app (solo en el router directo)
            # pero verificamos que la conexión se establece
            ws.send_json({"action": "end"})
            # Leer respuestas hasta el cierre
            messages = []
            try:
                while True:
                    msg = ws.receive_json()
                    messages.append(msg)
            except Exception:
                pass

        # Verificar que la llamada se completó
        resp = client.get(f"/api/calls/{call_id}", headers={"X-API-Key": api_key})
        data = resp.json()
        assert data["status"] == "completed"

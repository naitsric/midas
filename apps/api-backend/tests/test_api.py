import pytest
from fastapi.testclient import TestClient

from src.conversation.domain.entities import Conversation
from src.conversation.domain.value_objects import ConversationId
from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.intent.domain.entities import IntentResult
from src.intent.domain.ports import IntentDetector
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType
from src.main import create_app


class FakeIntentDetector(IntentDetector):
    def __init__(self, result: IntentResult):
        self._result = result

    async def detect(self, conversation: Conversation) -> IntentResult:
        return self._result


@pytest.fixture
def fake_detector():
    return FakeIntentDetector(
        IntentResult.detected(
            product_type=ProductType.MORTGAGE,
            confidence=Confidence(0.95),
            entities=ExtractedEntities(amount="250M COP", term="20 años", location="Bogotá"),
            summary="Busca crédito hipotecario",
        )
    )


@pytest.fixture
def repository():
    return InMemoryConversationRepository()


@pytest.fixture
def client(repository, fake_detector):
    app = create_app(conversation_repo=repository, intent_detector=fake_detector)
    return TestClient(app)


class TestHealthEndpoint:
    def test_health(self, client):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "ok"


class TestConversationEndpoints:
    def test_create_conversation(self, client):
        response = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        assert response.status_code == 201
        data = response.json()
        assert data["advisor_name"] == "Carlos"
        assert data["client_name"] == "María"
        assert "id" in data

    def test_get_conversation(self, client):
        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        conv_id = create_resp.json()["id"]

        response = client.get(f"/api/conversations/{conv_id}")
        assert response.status_code == 200
        assert response.json()["id"] == conv_id

    def test_get_nonexistent_returns_404(self, client):
        fake_id = str(ConversationId.generate())
        response = client.get(f"/api/conversations/{fake_id}")
        assert response.status_code == 404

    def test_add_message(self, client):
        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        conv_id = create_resp.json()["id"]

        response = client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "Carlos", "is_advisor": True, "text": "Hola María"},
        )
        assert response.status_code == 201
        assert response.json()["message_count"] == 1

    def test_add_message_to_nonexistent_returns_404(self, client):
        fake_id = str(ConversationId.generate())
        response = client.post(
            f"/api/conversations/{fake_id}/messages",
            json={"sender_name": "Carlos", "is_advisor": True, "text": "Hola"},
        )
        assert response.status_code == 404

    def test_add_multiple_messages_and_get(self, client):
        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        conv_id = create_resp.json()["id"]

        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "Carlos", "is_advisor": True, "text": "Hola"},
        )
        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "María", "is_advisor": False, "text": "Necesito un crédito"},
        )

        response = client.get(f"/api/conversations/{conv_id}")
        assert response.json()["message_count"] == 2
        assert len(response.json()["messages"]) == 2


class TestIntentEndpoints:
    def test_detect_intent(self, client):
        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        conv_id = create_resp.json()["id"]

        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "María", "is_advisor": False, "text": "Quiero un crédito hipotecario"},
        )

        response = client.post(f"/api/conversations/{conv_id}/detect-intent")
        assert response.status_code == 200
        data = response.json()
        assert data["intent_detected"] is True
        assert data["product_type"] == "mortgage"
        assert data["confidence"] == 0.95
        assert data["is_actionable"] is True

    def test_detect_intent_empty_conversation_returns_422(self, client):
        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        conv_id = create_resp.json()["id"]

        response = client.post(f"/api/conversations/{conv_id}/detect-intent")
        assert response.status_code == 422

    def test_detect_intent_nonexistent_conversation_returns_404(self, client):
        fake_id = str(ConversationId.generate())
        response = client.post(f"/api/conversations/{fake_id}/detect-intent")
        assert response.status_code == 404

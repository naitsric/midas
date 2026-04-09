import os

import pytest
from fastapi.testclient import TestClient

from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.intent.infrastructure.gemini_adapter import GeminiIntentDetector
from src.main import create_app


def _get_api_key() -> str:
    key = os.getenv("GEMINI_API_KEY", "")
    if not key:
        pytest.skip("GEMINI_API_KEY no configurada")
    return key


@pytest.fixture
def client():
    repo = InMemoryConversationRepository()
    detector = GeminiIntentDetector(api_key=_get_api_key())
    app = create_app(conversation_repo=repo, intent_detector=detector)
    return TestClient(app)


@pytest.mark.e2e
class TestFullAdvisorFlow:
    """Flujo completo tal como lo usaría la extensión Chrome."""

    def test_mortgage_flow(self, client):
        # 1. Asesor abre conversación
        resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        assert resp.status_code == 201
        conv_id = resp.json()["id"]

        # 2. Se capturan mensajes de la conversación
        messages = [
            {"sender_name": "Carlos", "is_advisor": True, "text": "Hola María, ¿en qué te puedo ayudar?"},
            {
                "sender_name": "María",
                "is_advisor": False,
                "text": "Necesito un crédito hipotecario para un apartamento en Bogotá de 250 millones a 20 años.",
            },
            {"sender_name": "Carlos", "is_advisor": True, "text": "Perfecto, déjame revisar opciones para ti."},
        ]
        for msg in messages:
            resp = client.post(f"/api/conversations/{conv_id}/messages", json=msg)
            assert resp.status_code == 201

        # 3. Verificar que la conversación tiene todos los mensajes
        resp = client.get(f"/api/conversations/{conv_id}")
        assert resp.json()["message_count"] == 3

        # 4. Detectar intención financiera
        resp = client.post(f"/api/conversations/{conv_id}/detect-intent")
        assert resp.status_code == 200
        data = resp.json()
        assert data["intent_detected"] is True
        assert data["product_type"] == "mortgage"
        assert data["is_actionable"] is True
        assert data["confidence"] >= 0.7

    def test_casual_conversation_no_intent(self, client):
        # 1. Conversación casual sin intención financiera
        resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "Pedro"},
        )
        conv_id = resp.json()["id"]

        messages = [
            {"sender_name": "Carlos", "is_advisor": True, "text": "Hola Pedro, ¿cómo vas?"},
            {"sender_name": "Pedro", "is_advisor": False, "text": "Todo bien, solo saludando. ¿Viste el partido?"},
            {"sender_name": "Carlos", "is_advisor": True, "text": "Sí, estuvo bueno. Hablamos luego."},
        ]
        for msg in messages:
            client.post(f"/api/conversations/{conv_id}/messages", json=msg)

        # 2. No debe detectar intención
        resp = client.post(f"/api/conversations/{conv_id}/detect-intent")
        assert resp.status_code == 200
        data = resp.json()
        assert data["intent_detected"] is False
        assert data["is_actionable"] is False

    def test_auto_loan_flow(self, client):
        resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "Luis"},
        )
        conv_id = resp.json()["id"]

        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={
                "sender_name": "Luis",
                "is_advisor": False,
                "text": "Carlos, quiero financiar una camioneta de 80 millones. "
                "¿Qué opciones de crédito vehicular hay?",
            },
        )

        resp = client.post(f"/api/conversations/{conv_id}/detect-intent")
        assert resp.status_code == 200
        data = resp.json()
        assert data["intent_detected"] is True
        assert data["product_type"] == "auto_loan"
        assert data["is_actionable"] is True

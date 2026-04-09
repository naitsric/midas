import pytest
from fastapi.testclient import TestClient

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.ports import ApplicationGenerator
from src.application.domain.value_objects import ApplicantData, ProductRequest
from src.application.infrastructure.adapters import InMemoryApplicationRepository
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


class FakeApplicationGenerator(ApplicationGenerator):
    async def generate(
        self, advisor_id: AdvisorId, conversation: Conversation, intent: IntentResult
    ) -> CreditApplication:
        return CreditApplication.create(
            advisor_id=advisor_id,
            applicant=ApplicantData(
                full_name=conversation.client_name,
                estimated_income=intent.entities.additional.get("income"),
            ),
            product_request=ProductRequest(
                product_type=intent.product_type,
                amount=intent.entities.amount,
                term=intent.entities.term,
                location=intent.entities.location,
            ),
            conversation_summary=intent.summary,
        )


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
def application_repo():
    return InMemoryApplicationRepository()


@pytest.fixture
def fake_generator():
    return FakeApplicationGenerator()


@pytest.fixture
def client(repository, fake_detector, application_repo, fake_generator):
    app = create_app(
        conversation_repo=repository,
        intent_detector=fake_detector,
        application_repo=application_repo,
        application_generator=fake_generator,
    )
    return TestClient(app)


def _register_advisor(client: TestClient) -> tuple[str, str]:
    """Registra un asesor y retorna (api_key, advisor_id)."""
    resp = client.post(
        "/api/advisors",
        json={"name": "Carlos Pérez", "email": "carlos@example.com", "phone": "3001234567"},
    )
    data = resp.json()
    return data["api_key"], data["id"]


def _auth_headers(api_key: str) -> dict[str, str]:
    return {"X-API-Key": api_key}


class TestHealthEndpoint:
    def test_health(self, client):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "ok"


class TestAdvisorEndpoints:
    def test_register_advisor(self, client):
        response = client.post(
            "/api/advisors",
            json={"name": "Carlos Pérez", "email": "carlos@example.com", "phone": "3001234567"},
        )
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "Carlos Pérez"
        assert data["email"] == "carlos@example.com"
        assert data["status"] == "active"
        assert "api_key" in data
        assert data["api_key"].startswith("midas_")

    def test_register_duplicate_email_returns_422(self, client):
        client.post(
            "/api/advisors",
            json={"name": "Carlos", "email": "carlos@example.com", "phone": "300"},
        )
        response = client.post(
            "/api/advisors",
            json={"name": "Otro", "email": "carlos@example.com", "phone": "301"},
        )
        assert response.status_code == 422

    def test_get_me_with_valid_key(self, client):
        reg_resp = client.post(
            "/api/advisors",
            json={"name": "Carlos", "email": "c@e.com", "phone": "300"},
        )
        api_key = reg_resp.json()["api_key"]

        response = client.get("/api/advisors/me", headers=_auth_headers(api_key))
        assert response.status_code == 200
        assert response.json()["name"] == "Carlos"
        assert "api_key" not in response.json()
        assert "api_key_masked" in response.json()

    def test_get_me_without_key_returns_401(self, client):
        response = client.get("/api/advisors/me")
        assert response.status_code == 401

    def test_get_me_with_invalid_key_returns_401(self, client):
        response = client.get("/api/advisors/me", headers=_auth_headers("midas_invalid"))
        assert response.status_code == 401


class TestConversationEndpoints:
    def test_create_conversation(self, client):
        api_key, _ = _register_advisor(client)

        response = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=_auth_headers(api_key),
        )
        assert response.status_code == 201
        data = response.json()
        assert data["advisor_name"] == "Carlos"
        assert data["client_name"] == "María"
        assert "id" in data

    def test_create_conversation_without_auth_returns_401(self, client):
        response = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
        )
        assert response.status_code == 401

    def test_get_conversation(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=headers,
        )
        conv_id = create_resp.json()["id"]

        response = client.get(f"/api/conversations/{conv_id}", headers=headers)
        assert response.status_code == 200
        assert response.json()["id"] == conv_id

    def test_get_nonexistent_returns_404(self, client):
        api_key, _ = _register_advisor(client)
        fake_id = str(ConversationId.generate())
        response = client.get(f"/api/conversations/{fake_id}", headers=_auth_headers(api_key))
        assert response.status_code == 404

    def test_add_message(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=headers,
        )
        conv_id = create_resp.json()["id"]

        response = client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "Carlos", "is_advisor": True, "text": "Hola María"},
            headers=headers,
        )
        assert response.status_code == 201
        assert response.json()["message_count"] == 1

    def test_add_message_to_nonexistent_returns_404(self, client):
        api_key, _ = _register_advisor(client)
        fake_id = str(ConversationId.generate())
        response = client.post(
            f"/api/conversations/{fake_id}/messages",
            json={"sender_name": "Carlos", "is_advisor": True, "text": "Hola"},
            headers=_auth_headers(api_key),
        )
        assert response.status_code == 404

    def test_add_multiple_messages_and_get(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=headers,
        )
        conv_id = create_resp.json()["id"]

        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "Carlos", "is_advisor": True, "text": "Hola"},
            headers=headers,
        )
        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "María", "is_advisor": False, "text": "Necesito un crédito"},
            headers=headers,
        )

        response = client.get(f"/api/conversations/{conv_id}", headers=headers)
        assert response.json()["message_count"] == 2
        assert len(response.json()["messages"]) == 2

    def test_cannot_access_other_advisors_conversation(self, client):
        # Registrar dos asesores
        api_key_1, _ = _register_advisor(client)
        resp2 = client.post(
            "/api/advisors",
            json={"name": "Otro", "email": "otro@example.com", "phone": "301"},
        )
        api_key_2 = resp2.json()["api_key"]

        # Crear conversación con asesor 1
        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=_auth_headers(api_key_1),
        )
        conv_id = create_resp.json()["id"]

        # Asesor 2 no puede acceder
        response = client.get(f"/api/conversations/{conv_id}", headers=_auth_headers(api_key_2))
        assert response.status_code == 404

    def test_list_conversations_empty(self, client):
        api_key, _ = _register_advisor(client)
        response = client.get("/api/conversations", headers=_auth_headers(api_key))
        assert response.status_code == 200
        assert response.json() == []

    def test_list_conversations(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        client.post("/api/conversations", json={"advisor_name": "Carlos", "client_name": "María"}, headers=headers)
        client.post("/api/conversations", json={"advisor_name": "Carlos", "client_name": "Pedro"}, headers=headers)

        response = client.get("/api/conversations", headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        # Respuesta resumida sin mensajes
        assert "messages" not in data[0]
        assert "message_count" in data[0]

    def test_list_conversations_only_own(self, client):
        api_key_1, _ = _register_advisor(client)
        resp2 = client.post(
            "/api/advisors",
            json={"name": "Otro", "email": "otro@example.com", "phone": "301"},
        )
        api_key_2 = resp2.json()["api_key"]

        # Asesor 1 crea 2 conversaciones
        client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=_auth_headers(api_key_1),
        )
        # Asesor 2 crea 1 conversación
        client.post(
            "/api/conversations",
            json={"advisor_name": "Otro", "client_name": "Luis"},
            headers=_auth_headers(api_key_2),
        )

        # Asesor 1 solo ve su conversación
        response = client.get("/api/conversations", headers=_auth_headers(api_key_1))
        assert len(response.json()) == 1
        assert response.json()[0]["client_name"] == "María"

    def test_import_conversation(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        response = client.post(
            "/api/conversations/import",
            json={
                "advisor_name": "Carlos",
                "client_name": "María",
                "messages": [
                    {"sender_name": "Carlos", "is_advisor": True, "text": "Hola María"},
                    {"sender_name": "María", "is_advisor": False, "text": "Necesito un crédito hipotecario"},
                    {"sender_name": "Carlos", "is_advisor": True, "text": "Claro, ¿de cuánto?"},
                ],
            },
            headers=headers,
        )
        assert response.status_code == 201
        data = response.json()
        assert data["advisor_name"] == "Carlos"
        assert data["client_name"] == "María"
        assert data["message_count"] == 3
        assert len(data["messages"]) == 3
        assert data["messages"][0]["text"] == "Hola María"
        assert data["messages"][1]["is_advisor"] is False

    def test_import_conversation_empty_messages_returns_422(self, client):
        api_key, _ = _register_advisor(client)
        response = client.post(
            "/api/conversations/import",
            json={"advisor_name": "Carlos", "client_name": "María", "messages": []},
            headers=_auth_headers(api_key),
        )
        assert response.status_code == 422

    def test_import_conversation_appears_in_list(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        client.post(
            "/api/conversations/import",
            json={
                "advisor_name": "Carlos",
                "client_name": "María",
                "messages": [
                    {"sender_name": "María", "is_advisor": False, "text": "Quiero un crédito"},
                ],
            },
            headers=headers,
        )

        response = client.get("/api/conversations", headers=headers)
        assert len(response.json()) == 1
        assert response.json()[0]["client_name"] == "María"
        assert response.json()[0]["message_count"] == 1

    def test_import_without_auth_returns_401(self, client):
        response = client.post(
            "/api/conversations/import",
            json={
                "advisor_name": "Carlos",
                "client_name": "María",
                "messages": [{"sender_name": "Carlos", "is_advisor": True, "text": "Hola"}],
            },
        )
        assert response.status_code == 401


class TestIntentEndpoints:
    def test_detect_intent(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=headers,
        )
        conv_id = create_resp.json()["id"]

        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "María", "is_advisor": False, "text": "Quiero un crédito hipotecario"},
            headers=headers,
        )

        response = client.post(f"/api/conversations/{conv_id}/detect-intent", headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["intent_detected"] is True
        assert data["product_type"] == "mortgage"
        assert data["confidence"] == 0.95
        assert data["is_actionable"] is True

    def test_detect_intent_empty_conversation_returns_422(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        create_resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=headers,
        )
        conv_id = create_resp.json()["id"]

        response = client.post(f"/api/conversations/{conv_id}/detect-intent", headers=headers)
        assert response.status_code == 422

    def test_detect_intent_nonexistent_conversation_returns_404(self, client):
        api_key, _ = _register_advisor(client)
        fake_id = str(ConversationId.generate())
        response = client.post(f"/api/conversations/{fake_id}/detect-intent", headers=_auth_headers(api_key))
        assert response.status_code == 404


class TestApplicationEndpoints:
    def _create_conversation_with_messages(self, client, headers):
        resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María García"},
            headers=headers,
        )
        conv_id = resp.json()["id"]
        client.post(
            f"/api/conversations/{conv_id}/messages",
            json={"sender_name": "María García", "is_advisor": False, "text": "Necesito un crédito hipotecario"},
            headers=headers,
        )
        return conv_id

    def test_generate_application(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)
        conv_id = self._create_conversation_with_messages(client, headers)

        response = client.post(f"/api/conversations/{conv_id}/generate-application", headers=headers)
        assert response.status_code == 201
        data = response.json()
        assert data["status"] == "draft"
        assert data["status_label"] == "Borrador"
        assert data["applicant"]["full_name"] == "María García"
        assert data["product_request"]["product_type"] == "mortgage"
        assert data["product_request"]["product_label"] == "Crédito Hipotecario"
        assert data["product_request"]["amount"] == "250M COP"

    def test_generate_application_nonexistent_conversation(self, client):
        api_key, _ = _register_advisor(client)
        fake_id = str(ConversationId.generate())
        response = client.post(f"/api/conversations/{fake_id}/generate-application", headers=_auth_headers(api_key))
        assert response.status_code == 404

    def test_generate_application_empty_conversation(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        resp = client.post(
            "/api/conversations",
            json={"advisor_name": "Carlos", "client_name": "María"},
            headers=headers,
        )
        conv_id = resp.json()["id"]

        response = client.post(f"/api/conversations/{conv_id}/generate-application", headers=headers)
        assert response.status_code == 422

    def test_get_application(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)
        conv_id = self._create_conversation_with_messages(client, headers)

        gen_resp = client.post(f"/api/conversations/{conv_id}/generate-application", headers=headers)
        app_id = gen_resp.json()["id"]

        response = client.get(f"/api/applications/{app_id}", headers=headers)
        assert response.status_code == 200
        assert response.json()["id"] == app_id
        assert response.json()["applicant"]["completeness"] > 0

    def test_get_nonexistent_application(self, client):
        from src.application.domain.value_objects import ApplicationId

        api_key, _ = _register_advisor(client)
        fake_id = str(ApplicationId.generate())
        response = client.get(f"/api/applications/{fake_id}", headers=_auth_headers(api_key))
        assert response.status_code == 404

    def test_list_applications_empty(self, client):
        api_key, _ = _register_advisor(client)
        response = client.get("/api/applications", headers=_auth_headers(api_key))
        assert response.status_code == 200
        assert response.json() == []

    def test_list_applications(self, client):
        api_key, _ = _register_advisor(client)
        headers = _auth_headers(api_key)

        # Generar 2 solicitudes
        conv_id_1 = self._create_conversation_with_messages(client, headers)
        client.post(f"/api/conversations/{conv_id_1}/generate-application", headers=headers)

        conv_id_2 = self._create_conversation_with_messages(client, headers)
        client.post(f"/api/conversations/{conv_id_2}/generate-application", headers=headers)

        response = client.get("/api/applications", headers=headers)
        assert response.status_code == 200
        assert len(response.json()) == 2

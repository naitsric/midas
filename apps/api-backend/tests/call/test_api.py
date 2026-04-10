import pytest
from fastapi.testclient import TestClient

from src.call.infrastructure.adapters import InMemoryCallRepository
from src.conversation.infrastructure.adapters import InMemoryConversationRepository
from src.intent.domain.entities import IntentResult
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType
from src.main import create_app
from tests.test_api import FakeApplicationGenerator, FakeIntentDetector


@pytest.fixture
def call_repo():
    return InMemoryCallRepository()


@pytest.fixture
def client(call_repo):
    fake_detector = FakeIntentDetector(
        IntentResult.detected(
            product_type=ProductType.MORTGAGE,
            confidence=Confidence(0.95),
            entities=ExtractedEntities(amount="250M COP"),
            summary="Busca hipoteca",
        )
    )
    app = create_app(
        conversation_repo=InMemoryConversationRepository(),
        intent_detector=fake_detector,
        application_repo=None,
        application_generator=FakeApplicationGenerator(),
        call_repo=call_repo,
    )
    return TestClient(app)


def _register_advisor(client: TestClient) -> tuple[str, str]:
    resp = client.post(
        "/api/advisors",
        json={"name": "Carlos Pérez", "email": "carlos@calls.com", "phone": "3001234567"},
    )
    data = resp.json()
    return data["api_key"], data["id"]


def _auth(api_key: str) -> dict[str, str]:
    return {"X-API-Key": api_key}


class TestStartCall:
    def test_start_call(self, client):
        key, _ = _register_advisor(client)
        resp = client.post("/api/calls", json={"client_name": "María García"}, headers=_auth(key))
        assert resp.status_code == 201
        data = resp.json()
        assert data["client_name"] == "María García"
        assert data["status"] == "recording"
        assert data["transcript"] == ""

    def test_start_call_no_auth(self, client):
        resp = client.post("/api/calls", json={"client_name": "María"})
        assert resp.status_code == 401


class TestListCalls:
    def test_list_empty(self, client):
        key, _ = _register_advisor(client)
        resp = client.get("/api/calls", headers=_auth(key))
        assert resp.status_code == 200
        assert resp.json() == []

    def test_list_with_calls(self, client):
        key, _ = _register_advisor(client)
        headers = _auth(key)
        client.post("/api/calls", json={"client_name": "Cliente A"}, headers=headers)
        client.post("/api/calls", json={"client_name": "Cliente B"}, headers=headers)

        resp = client.get("/api/calls", headers=headers)
        assert resp.status_code == 200
        assert len(resp.json()) == 2

    def test_list_isolation(self, client):
        key_a, _ = _register_advisor(client)
        resp_b = client.post(
            "/api/advisors",
            json={"name": "Otro", "email": "otro@calls.com", "phone": "3009999999"},
        )
        key_b = resp_b.json()["api_key"]

        client.post("/api/calls", json={"client_name": "Cliente A"}, headers=_auth(key_a))
        client.post("/api/calls", json={"client_name": "Cliente B"}, headers=_auth(key_b))

        resp = client.get("/api/calls", headers=_auth(key_a))
        assert len(resp.json()) == 1
        assert resp.json()[0]["client_name"] == "Cliente A"


class TestGetCall:
    def test_get_call(self, client):
        key, _ = _register_advisor(client)
        headers = _auth(key)
        create_resp = client.post("/api/calls", json={"client_name": "María"}, headers=headers)
        call_id = create_resp.json()["id"]

        resp = client.get(f"/api/calls/{call_id}", headers=headers)
        assert resp.status_code == 200
        assert resp.json()["client_name"] == "María"

    def test_get_call_not_found(self, client):
        key, _ = _register_advisor(client)
        resp = client.get("/api/calls/00000000-0000-0000-0000-000000000000", headers=_auth(key))
        assert resp.status_code == 404


class TestEndCall:
    def test_end_call(self, client):
        key, _ = _register_advisor(client)
        headers = _auth(key)
        create_resp = client.post("/api/calls", json={"client_name": "María"}, headers=headers)
        call_id = create_resp.json()["id"]

        resp = client.post(f"/api/calls/{call_id}/end", headers=headers)
        assert resp.status_code == 200
        assert resp.json()["status"] == "completed"

    def test_end_call_not_found(self, client):
        key, _ = _register_advisor(client)
        resp = client.post(
            "/api/calls/00000000-0000-0000-0000-000000000000/end",
            headers=_auth(key),
        )
        assert resp.status_code == 404

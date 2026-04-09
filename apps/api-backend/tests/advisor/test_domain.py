import secrets
from uuid import uuid4

import pytest

from src.advisor.domain.entities import Advisor
from src.advisor.domain.exceptions import InvalidAdvisorError
from src.advisor.domain.value_objects import AdvisorId, AdvisorStatus, ApiKey


class TestAdvisorId:
    def test_generate_unique(self):
        id1 = AdvisorId.generate()
        id2 = AdvisorId.generate()
        assert id1 != id2

    def test_create_with_uuid(self):
        uid = uuid4()
        assert AdvisorId(uid).value == uid

    def test_equality(self):
        uid = uuid4()
        assert AdvisorId(uid) == AdvisorId(uid)


class TestAdvisorStatus:
    def test_all_statuses(self):
        assert AdvisorStatus.ACTIVE is not None
        assert AdvisorStatus.SUSPENDED is not None
        assert AdvisorStatus.DEACTIVATED is not None

    def test_labels(self):
        assert AdvisorStatus.ACTIVE.label == "Activo"
        assert AdvisorStatus.SUSPENDED.label == "Suspendido"
        assert AdvisorStatus.DEACTIVATED.label == "Desactivado"


class TestApiKey:
    def test_generate(self):
        key = ApiKey.generate()
        assert key.value.startswith("midas_")
        assert len(key.value) > 10

    def test_generate_unique(self):
        key1 = ApiKey.generate()
        key2 = ApiKey.generate()
        assert key1 != key2

    def test_create_from_string(self):
        raw = "midas_" + secrets.token_urlsafe(32)
        key = ApiKey(raw)
        assert key.value == raw

    def test_masked(self):
        key = ApiKey.generate()
        masked = key.masked
        assert masked.startswith("midas_")
        assert masked.endswith("****")
        assert len(masked) < len(key.value)


class TestAdvisor:
    def test_register(self):
        advisor = Advisor.register(name="Carlos Pérez", email="carlos@example.com", phone="3001234567")
        assert advisor.id is not None
        assert advisor.name == "Carlos Pérez"
        assert advisor.email == "carlos@example.com"
        assert advisor.phone == "3001234567"
        assert advisor.status == AdvisorStatus.ACTIVE
        assert advisor.api_key is not None

    def test_empty_name_raises(self):
        with pytest.raises(InvalidAdvisorError, match="nombre"):
            Advisor.register(name="", email="carlos@example.com", phone="300")

    def test_empty_email_raises(self):
        with pytest.raises(InvalidAdvisorError, match="email"):
            Advisor.register(name="Carlos", email="", phone="300")

    def test_suspend(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        advisor.suspend(reason="Uso indebido")
        assert advisor.status == AdvisorStatus.SUSPENDED
        assert advisor.suspension_reason == "Uso indebido"

    def test_cannot_suspend_already_suspended(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        advisor.suspend(reason="Test")
        with pytest.raises(InvalidAdvisorError, match="estado"):
            advisor.suspend(reason="Otra vez")

    def test_reactivate(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        advisor.suspend(reason="Test")
        advisor.reactivate()
        assert advisor.status == AdvisorStatus.ACTIVE
        assert advisor.suspension_reason is None

    def test_deactivate(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        advisor.deactivate()
        assert advisor.status == AdvisorStatus.DEACTIVATED

    def test_cannot_use_deactivated(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        advisor.deactivate()
        with pytest.raises(InvalidAdvisorError, match="desactivado"):
            advisor.suspend(reason="Test")

    def test_regenerate_api_key(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        old_key = advisor.api_key
        advisor.regenerate_api_key()
        assert advisor.api_key != old_key

    def test_is_active(self):
        advisor = Advisor.register(name="Carlos", email="c@e.com", phone="300")
        assert advisor.is_active is True
        advisor.suspend(reason="Test")
        assert advisor.is_active is False

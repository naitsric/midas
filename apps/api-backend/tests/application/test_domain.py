from uuid import uuid4

import pytest

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.exceptions import InvalidApplicationError
from src.application.domain.value_objects import (
    ApplicantData,
    ApplicationId,
    ApplicationStatus,
    ProductRequest,
)
from src.intent.domain.value_objects import ProductType


class TestApplicationId:
    def test_generate_unique(self):
        id1 = ApplicationId.generate()
        id2 = ApplicationId.generate()
        assert id1 != id2

    def test_create_with_uuid(self):
        uid = uuid4()
        assert ApplicationId(uid).value == uid

    def test_equality(self):
        uid = uuid4()
        assert ApplicationId(uid) == ApplicationId(uid)


class TestApplicationStatus:
    def test_all_statuses_exist(self):
        assert ApplicationStatus.DRAFT is not None
        assert ApplicationStatus.REVIEW is not None
        assert ApplicationStatus.SUBMITTED is not None
        assert ApplicationStatus.REJECTED is not None

    def test_label_in_spanish(self):
        assert ApplicationStatus.DRAFT.label == "Borrador"
        assert ApplicationStatus.REVIEW.label == "En Revisión"
        assert ApplicationStatus.SUBMITTED.label == "Enviada"
        assert ApplicationStatus.REJECTED.label == "Rechazada"


class TestApplicantData:
    def test_create_with_all_fields(self):
        data = ApplicantData(
            full_name="María García",
            phone="3001234567",
            estimated_income="8,000,000 COP/mes",
            employment_type="Empleada",
        )
        assert data.full_name == "María García"
        assert data.phone == "3001234567"

    def test_create_minimal(self):
        data = ApplicantData(full_name="María García")
        assert data.full_name == "María García"
        assert data.phone is None
        assert data.estimated_income is None

    def test_empty_name_raises(self):
        with pytest.raises(ValueError, match="nombre"):
            ApplicantData(full_name="")

    def test_completeness_all_fields(self):
        data = ApplicantData(
            full_name="María García",
            phone="3001234567",
            estimated_income="8M COP",
            employment_type="Empleada",
        )
        assert data.completeness == 1.0

    def test_completeness_only_name(self):
        data = ApplicantData(full_name="María García")
        assert data.completeness == 0.25

    def test_completeness_partial(self):
        data = ApplicantData(full_name="María", phone="300123")
        assert data.completeness == 0.5


class TestProductRequest:
    def test_create(self):
        req = ProductRequest(
            product_type=ProductType.MORTGAGE,
            amount="250,000,000 COP",
            term="20 años",
            location="Bogotá",
        )
        assert req.product_type == ProductType.MORTGAGE
        assert req.amount == "250,000,000 COP"

    def test_create_minimal(self):
        req = ProductRequest(product_type=ProductType.AUTO_LOAN)
        assert req.amount is None
        assert req.term is None
        assert req.location is None

    def test_summary(self):
        req = ProductRequest(
            product_type=ProductType.MORTGAGE,
            amount="250M COP",
            term="20 años",
            location="Bogotá",
        )
        summary = req.summary
        assert "Crédito Hipotecario" in summary
        assert "250M COP" in summary
        assert "20 años" in summary
        assert "Bogotá" in summary

    def test_summary_minimal(self):
        req = ProductRequest(product_type=ProductType.INSURANCE)
        assert "Seguro" in req.summary


class TestCreditApplication:
    def _applicant(self):
        return ApplicantData(full_name="María García", phone="3001234567")

    def _product(self):
        return ProductRequest(
            product_type=ProductType.MORTGAGE,
            amount="250M COP",
            term="20 años",
            location="Bogotá",
        )

    def test_create(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Cliente busca hipotecario en Bogotá",
        )
        assert app.id is not None
        assert app.status == ApplicationStatus.DRAFT
        assert app.applicant.full_name == "María García"
        assert app.product_request.product_type == ProductType.MORTGAGE

    def test_submit_for_review(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        app.submit_for_review()
        assert app.status == ApplicationStatus.REVIEW

    def test_cannot_submit_already_submitted(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        app.submit_for_review()
        with pytest.raises(InvalidApplicationError, match="estado"):
            app.submit_for_review()

    def test_mark_submitted(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        app.submit_for_review()
        app.mark_submitted()
        assert app.status == ApplicationStatus.SUBMITTED

    def test_cannot_mark_submitted_from_draft(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        with pytest.raises(InvalidApplicationError, match="estado"):
            app.mark_submitted()

    def test_reject(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        app.submit_for_review()
        app.reject(reason="Datos insuficientes")
        assert app.status == ApplicationStatus.REJECTED
        assert app.rejection_reason == "Datos insuficientes"

    def test_update_applicant_data(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=ApplicantData(full_name="María"),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        new_data = ApplicantData(full_name="María García", phone="300123", estimated_income="8M COP")
        app.update_applicant(new_data)
        assert app.applicant.full_name == "María García"
        assert app.applicant.estimated_income == "8M COP"

    def test_cannot_update_after_submitted(self):
        app = CreditApplication.create(
            advisor_id=AdvisorId.generate(),
            applicant=self._applicant(),
            product_request=self._product(),
            conversation_summary="Resumen",
        )
        app.submit_for_review()
        app.mark_submitted()
        with pytest.raises(InvalidApplicationError, match="modificar"):
            app.update_applicant(ApplicantData(full_name="Otra"))

"""
Tools para que el agente lea las solicitudes de crédito del asesor autenticado.
"""

from uuid import UUID

from google.adk.tools import FunctionTool

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.ports import ApplicationRepository
from src.application.domain.value_objects import ApplicationId, ApplicationStatus


def build_applications_tools(
    advisor_id: AdvisorId, repo: ApplicationRepository
) -> list[FunctionTool]:
    async def list_applications(status_filter: str = "") -> dict:
        """Lista las solicitudes de crédito del asesor.

        `status_filter`: 'draft', 'review', 'submitted', 'rejected' o "" (todas).

        Usar cuando el asesor pregunta por sus solicitudes, borradores
        pendientes, solicitudes enviadas, o necesita el id de una solicitud.
        """
        apps = await repo.find_all_by_advisor(advisor_id)
        if status_filter and status_filter.strip():
            try:
                status = ApplicationStatus(status_filter.lower().strip())
                apps = [a for a in apps if a.status == status]
            except ValueError:
                return {
                    "error": f"status_filter inválido: {status_filter!r}. "
                    "Usar 'draft', 'review', 'submitted' o 'rejected'."
                }

        items = [
            {
                "id": str(a.id),
                "applicant_name": a.applicant.full_name,
                "product": a.product_request.product_type.label,
                "status": a.status.value,
                "completeness": round(a.applicant.completeness, 2),
                "created_at": a.created_at.isoformat(),
            }
            for a in apps
        ]
        return {"applications": items, "count": len(items)}

    async def get_application_detail(application_id: str) -> dict:
        """Devuelve el detalle completo de una solicitud de crédito.

        `application_id` es el UUID que devuelve `list_applications`.

        Usar cuando el asesor pide ver una solicitud específica, qué le falta,
        a qué producto aplica, o por qué fue rechazada.
        """
        try:
            aid = ApplicationId(UUID(application_id))
        except (ValueError, AttributeError):
            return {"error": f"application_id inválido: {application_id!r}"}

        app = await repo.find_by_id_and_advisor(aid, advisor_id)
        if app is None:
            return {"error": "Solicitud no encontrada"}

        return {
            "id": str(app.id),
            "status": app.status.value,
            "status_label": app.status.label,
            "rejection_reason": app.rejection_reason,
            "applicant": {
                "full_name": app.applicant.full_name,
                "phone": app.applicant.phone,
                "estimated_income": app.applicant.estimated_income,
                "employment_type": app.applicant.employment_type,
                "completeness": round(app.applicant.completeness, 2),
            },
            "product_request": {
                "product_type": app.product_request.product_type.value,
                "product_label": app.product_request.product_type.label,
                "amount": app.product_request.amount,
                "term": app.product_request.term,
                "location": app.product_request.location,
                "summary": app.product_request.summary,
            },
            "conversation_summary": app.conversation_summary,
            "created_at": app.created_at.isoformat(),
        }

    return [FunctionTool(list_applications), FunctionTool(get_application_detail)]

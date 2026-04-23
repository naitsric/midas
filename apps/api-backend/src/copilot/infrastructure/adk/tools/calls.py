"""
Tools para que el agente lea las llamadas del asesor autenticado.

El `advisor_id` se cierra en el closure — el LLM nunca lo ve y nunca puede
inyectarlo. Esto previene cualquier cross-tenant leak por prompt injection.
"""

from uuid import UUID

from google.adk.tools import FunctionTool

from src.advisor.domain.value_objects import AdvisorId
from src.call.domain.ports import CallRepository
from src.call.domain.value_objects import CallId


def build_calls_tools(advisor_id: AdvisorId, repo: CallRepository) -> list[FunctionTool]:
    async def list_recent_calls(limit: int = 10) -> dict:
        """Lista las llamadas más recientes del asesor.

        Devuelve hasta `limit` llamadas (default 10) ordenadas por fecha
        descendente, con id, cliente, estado, duración y fecha.

        Usar cuando el asesor pide ver sus llamadas, su pipeline de llamadas,
        o necesita el id de una llamada para obtener su transcripción.
        """
        calls = await repo.find_all_by_advisor(advisor_id)
        capped = max(1, min(limit, 50))
        items = [
            {
                "id": str(c.id),
                "client": c.client_name,
                "status": c.status.value,
                "duration_seconds": c.duration_seconds,
                "created_at": c.created_at.isoformat(),
                "has_transcript": bool(c.transcript and c.transcript.strip()),
            }
            for c in calls[:capped]
        ]
        return {"calls": items, "count": len(items), "total_available": len(calls)}

    async def get_call_transcript(call_id: str) -> dict:
        """Devuelve la transcripción completa de una llamada específica.

        `call_id` es el UUID que devuelve `list_recent_calls`. Si la llamada
        no existe o no pertenece al asesor, retorna error.

        Usar cuando el asesor pregunta qué dijo X cliente, qué pasó en una
        llamada específica, o necesita analizar el contenido de una llamada.
        """
        try:
            cid = CallId(UUID(call_id))
        except (ValueError, AttributeError):
            return {"error": f"call_id inválido: {call_id!r}"}

        call = await repo.find_by_id_and_advisor(cid, advisor_id)
        if call is None:
            return {"error": "Llamada no encontrada"}

        return {
            "id": str(call.id),
            "client": call.client_name,
            "status": call.status.value,
            "duration_seconds": call.duration_seconds,
            "created_at": call.created_at.isoformat(),
            "transcript": call.transcript or "(sin transcripción)",
        }

    return [FunctionTool(list_recent_calls), FunctionTool(get_call_transcript)]

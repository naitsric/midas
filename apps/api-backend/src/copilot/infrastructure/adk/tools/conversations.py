"""
Tool para que el agente busque conversaciones de WhatsApp del asesor.
"""

from google.adk.tools import FunctionTool

from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.ports import ConversationRepository


def build_conversations_tools(
    advisor_id: AdvisorId, repo: ConversationRepository
) -> list[FunctionTool]:
    async def search_conversations(client_name: str = "") -> dict:
        """Busca conversaciones de WhatsApp del asesor, opcionalmente
        filtradas por nombre de cliente (substring, case-insensitive).
        Pasá "" para listar todas.

        Devuelve resúmenes — para ver mensajes individuales, el asesor
        debería abrir la conversación en la app.

        Usar cuando el asesor pregunta qué clientes le escribieron, qué
        habló por WhatsApp con X cliente, o cuántas conversaciones tiene.
        """
        convs = await repo.find_all_by_advisor(advisor_id)
        if client_name and client_name.strip():
            needle = client_name.strip().lower()
            convs = [c for c in convs if needle in c.client_name.lower()]

        items = [
            {
                "id": str(c.id),
                "client_name": c.client_name,
                "message_count": c.message_count,
                "created_at": c.created_at.isoformat(),
            }
            for c in convs
        ]
        return {"conversations": items, "count": len(items)}

    return [FunctionTool(search_conversations)]

"""
Composición de tools y extracción de SourceRef para los chips de la UI.

Cada tool result que pasa por el agente se mapea a un SourceRef opcional
mediante `extract_source_ref(tool_name, tool_result)` — el endpoint SSE
emite ese SourceRef como `tool_result` event para que la app móvil lo
renderice como chip bajo la respuesta del asistente.
"""

from typing import Any

from google.adk.tools import FunctionTool

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.ports import ApplicationRepository
from src.call.domain.ports import CallRepository
from src.conversation.domain.ports import ConversationRepository
from src.copilot.domain.value_objects import SourceRef, SourceType
from src.copilot.infrastructure.adk.tools.applications import build_applications_tools
from src.copilot.infrastructure.adk.tools.calendar import (
    CLIENT_SIDE_TOOL_ACTIONS,
    build_calendar_tools,
    build_client_action_payload,
)
from src.copilot.infrastructure.adk.tools.calls import build_calls_tools
from src.copilot.infrastructure.adk.tools.conversations import build_conversations_tools


def build_all_tools(
    advisor_id: AdvisorId,
    *,
    call_repo: CallRepository,
    application_repo: ApplicationRepository,
    conversation_repo: ConversationRepository,
) -> list[FunctionTool]:
    return [
        *build_calls_tools(advisor_id, call_repo),
        *build_applications_tools(advisor_id, application_repo),
        *build_conversations_tools(advisor_id, conversation_repo),
        *build_calendar_tools(),
    ]


def extract_source_ref(tool_name: str, tool_result: Any) -> SourceRef | None:
    """Mapea un (tool_name, tool_result) a un SourceRef para mostrar como chip.

    Devuelve None si el resultado tiene error o no produce un chip útil.
    """
    if not isinstance(tool_result, dict) or tool_result.get("error"):
        return None

    if tool_name == "list_recent_calls":
        count = tool_result.get("count", 0)
        if count == 0:
            return None
        return SourceRef(type=SourceType.CALL, label=f"Llamadas · {count}")

    if tool_name == "get_call_transcript":
        client = tool_result.get("client", "")
        return SourceRef(type=SourceType.CALL, label=f"Llamada · {client}")

    if tool_name == "list_applications":
        count = tool_result.get("count", 0)
        if count == 0:
            return None
        return SourceRef(type=SourceType.APPLICATION, label=f"Solicitudes · {count}")

    if tool_name == "get_application_detail":
        applicant = tool_result.get("applicant", {})
        name = applicant.get("full_name", "") if isinstance(applicant, dict) else ""
        return SourceRef(type=SourceType.APPLICATION, label=f"Solicitud · {name}")

    if tool_name == "search_conversations":
        count = tool_result.get("count", 0)
        if count == 0:
            return None
        return SourceRef(type=SourceType.CHAT, label=f"WhatsApp · {count} chats")

    return None


__all__ = [
    "build_all_tools",
    "extract_source_ref",
    "CLIENT_SIDE_TOOL_ACTIONS",
    "build_client_action_payload",
]

"""
ADK adapter — implementación real de CopilotAgent.

Cada llamada a `run()` crea un Agent + Runner + Session efímero.
La sesión se popula con el historial recibido del cliente y se ejecuta
con el mensaje nuevo. Stateless desde el punto de vista del backend —
no hay persistencia entre requests.
"""

from collections.abc import AsyncIterator

from google.adk.agents import Agent
from google.adk.events import Event
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types as genai_types

from src.advisor.domain.entities import Advisor
from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.ports import ApplicationRepository
from src.call.domain.ports import CallRepository
from src.conversation.domain.ports import ConversationRepository
from src.copilot.domain.events import (
    CopilotEvent,
    TokenEvent,
    ToolCallEvent,
    ToolResultEvent,
)
from src.copilot.domain.ports import CopilotAgent
from src.copilot.domain.value_objects import CopilotMessage, MessageRole
from src.copilot.infrastructure.adk.system_prompt import build_system_prompt
from src.copilot.infrastructure.adk.tools import build_all_tools, extract_source_ref

APP_NAME = "midas-copilot"
DEFAULT_MODEL = "gemini-2.5-flash"


class AdkCopilotAgent(CopilotAgent):
    def __init__(
        self,
        *,
        call_repo: CallRepository,
        application_repo: ApplicationRepository,
        conversation_repo: ConversationRepository,
        model: str = DEFAULT_MODEL,
    ):
        self._call_repo = call_repo
        self._application_repo = application_repo
        self._conversation_repo = conversation_repo
        self._model = model

    async def run(  # type: ignore[override]
        self, advisor: Advisor, history: list[CopilotMessage], message: str
    ) -> AsyncIterator[CopilotEvent]:
        tools = build_all_tools(
            advisor.id,
            call_repo=self._call_repo,
            application_repo=self._application_repo,
            conversation_repo=self._conversation_repo,
        )
        agent = Agent(
            name="midas_copilot",
            model=self._model,
            instruction=build_system_prompt(advisor.name),
            tools=tools,
        )

        session_service = InMemorySessionService()
        user_id = str(advisor.id)
        session = await session_service.create_session(app_name=APP_NAME, user_id=user_id)

        # Pre-cargar historial en la sesión efímera. Cada mensaje del usuario
        # se appendea como Event author='user', cada respuesta del asistente
        # como author=agent.name. ADK usará estos eventos como contexto.
        for past in history:
            author = "user" if past.role == MessageRole.USER else agent.name
            await session_service.append_event(
                session,
                Event(
                    author=author,
                    invocation_id=f"hist-{author}-{len(session.events)}",
                    content=genai_types.Content(
                        role="user" if past.role == MessageRole.USER else "model",
                        parts=[genai_types.Part(text=past.text)],
                    ),
                ),
            )

        runner = Runner(app_name=APP_NAME, agent=agent, session_service=session_service)
        new_message = genai_types.Content(
            role="user", parts=[genai_types.Part(text=message)]
        )

        async for event in runner.run_async(
            user_id=user_id, session_id=session.id, new_message=new_message
        ):
            yielded_any = False

            # 1) Tool calls
            for fc in event.get_function_calls():
                yielded_any = True
                yield ToolCallEvent(name=fc.name)

            # 2) Tool responses → SourceRef chip
            for fr in event.get_function_responses():
                yielded_any = True
                source = extract_source_ref(fr.name, fr.response)
                yield ToolResultEvent(name=fr.name, source=source)

            # 3) Text deltas (parcial o completo) del modelo
            if event.content and event.content.parts:
                for part in event.content.parts:
                    text = getattr(part, "text", None)
                    if text:
                        yielded_any = True
                        yield TokenEvent(text=text)

            # Si el evento no tenía nada relevante (ej. control event), seguimos
            if not yielded_any:
                continue


def build_advisor_id_from_str(value: str) -> AdvisorId:
    """Helper para tests — construye AdvisorId desde su string."""
    from uuid import UUID
    return AdvisorId(UUID(value))

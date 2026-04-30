"""
Calendar tool — client-side.

El tool no crea el evento en el backend. Retorna `{queued: true, ...}` para
que el LLM lo interprete como "listo", y el adapter del agente se encarga
de emitir un `ClientActionEvent` por el stream SSE que la app iOS
intercepta y dispara via EventKit nativo.

Scope v0.1: solo `create_reminder`. Duración fija default 30 min.
"""

from google.adk.tools import FunctionTool


def build_calendar_tools() -> list[FunctionTool]:
    async def create_reminder(title: str, when_iso: str, duration_minutes: int = 30) -> dict:
        """Crea un recordatorio / evento en el calendar nativo del asesor.

        Usa cuando el asesor pida recordar algo, agendar una llamada, o poner
        una tarea en el calendar (ej. "recordame mañana 10am llamar a Juan").

        - `title`: descripción corta del recordatorio.
        - `when_iso`: fecha-hora ISO 8601 CON timezone offset (ej.
          "2026-04-24T10:00:00-05:00" para Colombia). Si el asesor dice
          "mañana", "el viernes", etc., resolvé la fecha en timezone
          America/Bogota (UTC-5) a menos que se indique otra zona.
        - `duration_minutes`: duración en minutos. Default 30 si no se
          menciona nada.

        Devuelve inmediatamente con `queued=true`. El evento se crea en el
        dispositivo del asesor, no en el servidor. Confirmá al asesor que
        quedó agendado y dale la hora en su zona horaria local.
        """
        return {
            "queued": True,
            "title": title,
            "when_iso": when_iso,
            "duration_minutes": duration_minutes,
            "summary": f"Recordatorio agendado: '{title}' en {when_iso}",
        }

    return [FunctionTool(create_reminder)]


# Mapeo tool_name → client_action_name.
# Un tool está en este dict si su "efecto" lo ejecuta el cliente (iOS app)
# además de retornar el dict al LLM.
CLIENT_SIDE_TOOL_ACTIONS: dict[str, str] = {
    "create_reminder": "create_event",
}


def build_client_action_payload(tool_name: str, tool_args: dict) -> dict:
    """Construye el payload JSON que va en `ClientActionEvent.payload`.

    El cliente móvil recibe exactamente este dict y lo dispatcha al bridge
    nativo correspondiente. Schema por action:

      create_event:
        { title: str, when_iso: str, duration_minutes: int }
    """
    if tool_name == "create_reminder":
        return {
            "title": tool_args.get("title", ""),
            "when_iso": tool_args.get("when_iso", ""),
            "duration_minutes": int(tool_args.get("duration_minutes", 30)),
        }
    return dict(tool_args)

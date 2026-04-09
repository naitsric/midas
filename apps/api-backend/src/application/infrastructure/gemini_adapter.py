import json

import google.generativeai as genai

from src.advisor.domain.value_objects import AdvisorId
from src.application.domain.entities import CreditApplication
from src.application.domain.exceptions import ApplicationGenerationError
from src.application.domain.ports import ApplicationGenerator
from src.application.domain.value_objects import ApplicantData, ProductRequest
from src.conversation.domain.entities import Conversation
from src.intent.domain.entities import IntentResult

SYSTEM_PROMPT = (
    "Eres un asistente de generación de solicitudes de crédito. "
    "A partir de una conversación entre un asesor financiero y un cliente, "
    "y un análisis de intención previo, extrae los datos del solicitante.\n\n"
    "Responde ÚNICAMENTE con JSON válido, sin markdown:\n"
    "{\n"
    '    "full_name": "nombre completo del cliente",\n'
    '    "phone": "teléfono si se menciona, o null",\n'
    '    "estimated_income": "ingreso estimado si se menciona, o null",\n'
    '    "employment_type": "tipo de empleo si se menciona, o null",\n'
    '    "conversation_summary": "resumen de 1-2 oraciones de la solicitud"\n'
    "}"
)


class GeminiApplicationGenerator(ApplicationGenerator):
    def __init__(self, api_key: str, model_name: str = "gemini-2.0-flash"):
        genai.configure(api_key=api_key)
        self._model = genai.GenerativeModel(model_name, system_instruction=SYSTEM_PROMPT)

    async def generate(
        self, advisor_id: AdvisorId, conversation: Conversation, intent: IntentResult
    ) -> CreditApplication:
        conversation_text = "\n".join(
            f"{'Asesor' if m.sender.is_advisor else 'Cliente'} ({m.sender.name}): {m.text}"
            for m in conversation.messages
        )

        prompt = (
            f"Conversación:\n{conversation_text}\n\n"
            f"Intención detectada: {intent.summary}\n"
            f"Producto: {intent.product_type.label if intent.product_type else 'N/A'}\n"
            f"Monto: {intent.entities.amount or 'No mencionado'}\n"
            f"Plazo: {intent.entities.term or 'No mencionado'}\n"
            f"Ubicación: {intent.entities.location or 'No mencionada'}"
        )

        try:
            response = self._model.generate_content(prompt)
            return self._parse_response(response.text, advisor_id, intent)
        except Exception as e:
            if isinstance(e, ApplicationGenerationError):
                raise
            raise ApplicationGenerationError(f"Error al generar solicitud con Gemini: {e}") from e

    def _parse_response(self, text: str, advisor_id: AdvisorId, intent: IntentResult) -> CreditApplication:
        try:
            data = json.loads(text.strip())
        except json.JSONDecodeError as e:
            raise ApplicationGenerationError(f"Respuesta de Gemini no es JSON válido: {text[:200]}") from e

        full_name = data.get("full_name", "").strip()
        if not full_name:
            raise ApplicationGenerationError("Gemini no pudo extraer el nombre del cliente")

        applicant = ApplicantData(
            full_name=full_name,
            phone=data.get("phone"),
            estimated_income=data.get("estimated_income"),
            employment_type=data.get("employment_type"),
        )

        product_request = ProductRequest(
            product_type=intent.product_type,
            amount=intent.entities.amount,
            term=intent.entities.term,
            location=intent.entities.location,
        )

        return CreditApplication.create(
            advisor_id=advisor_id,
            applicant=applicant,
            product_request=product_request,
            conversation_summary=data.get("conversation_summary", intent.summary),
        )

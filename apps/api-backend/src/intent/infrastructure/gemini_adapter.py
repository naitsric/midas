import json

import google.generativeai as genai

from src.conversation.domain.entities import Conversation
from src.intent.domain.entities import IntentResult
from src.intent.domain.exceptions import IntentDetectionError
from src.intent.domain.ports import IntentDetector
from src.intent.domain.value_objects import Confidence, ExtractedEntities, ProductType

PRODUCT_TYPE_MAP = {
    "mortgage": ProductType.MORTGAGE,
    "auto_loan": ProductType.AUTO_LOAN,
    "credit_card": ProductType.CREDIT_CARD,
    "personal_loan": ProductType.PERSONAL_LOAN,
    "refinance": ProductType.REFINANCE,
    "insurance": ProductType.INSURANCE,
}

SYSTEM_PROMPT = (
    "Eres un analizador de intención financiera. "
    "Analiza conversaciones entre asesores financieros y clientes.\n\n"
    "Detecta si hay intención clara de adquirir un producto financiero "
    "(mortgage, auto_loan, credit_card, personal_loan, refinance, insurance).\n\n"
    "Responde ÚNICAMENTE con JSON válido, sin markdown ni texto adicional:\n"
    "{\n"
    '    "intent_detected": true/false,\n'
    '    "confidence": 0.0-1.0,\n'
    '    "product_type": "mortgage" | "auto_loan" | "credit_card" | "personal_loan" | "refinance" | "insurance" | null,\n'
    '    "entities": {\n'
    '        "amount": "monto NORMALIZADO como número entero string sin símbolos ni separadores",\n'
    '        "term": "plazo NORMALIZADO en meses (entero string)",\n'
    '        "location": "ubicación o null"\n'
    "    },\n"
    '    "summary": "resumen breve de la intención detectada"\n'
    "}\n\n"
    "REGLAS DE NORMALIZACIÓN (importante para que el frontend pueda sumar):\n\n"
    "amount: convertí cualquier expresión a un entero string sin símbolos\n"
    "  ni separadores ni unidades. La moneda implícita es la local del cliente\n"
    "  (COP en Colombia, MXN en México). Ejemplos:\n"
    '    - "850 millones de pesos" → "850000000"\n'
    '    - "$1.2M" → "1200000"\n'
    '    - "300 mil dólares" → "300000"  (no convertir USD→COP, solo normalizar)\n'
    '    - "1,500,000" → "1500000"\n'
    '    - "entre 380 y 450 millones" → "415000000"  (promedio si es rango)\n'
    "    - no mencionado o ambiguo → null\n\n"
    "term: convertí a meses como entero string. Ejemplos:\n"
    '    - "20 años" → "240"\n'
    '    - "60 meses" → "60"\n'
    '    - "5 años" → "60"\n'
    "    - no mencionado → null\n\n"
    "location: ciudad/región como texto plano (ej. \"Bogotá\", \"Chapinero\").\n"
)


class GeminiIntentDetector(IntentDetector):
    def __init__(self, api_key: str, model_name: str = "gemini-2.5-flash"):
        genai.configure(api_key=api_key)
        self._model = genai.GenerativeModel(model_name, system_instruction=SYSTEM_PROMPT)

    async def detect(self, conversation: Conversation) -> IntentResult:
        conversation_text = "\n".join(
            f"{'Asesor' if m.sender.is_advisor else 'Cliente'} ({m.sender.name}): {m.text}"
            for m in conversation.messages
        )

        prompt = f"Analiza esta conversación:\n\n{conversation_text}"

        try:
            response = self._model.generate_content(prompt)
            return self._parse_response(response.text)
        except Exception as e:
            if isinstance(e, IntentDetectionError):
                raise
            raise IntentDetectionError(f"Error al comunicarse con Gemini: {e}") from e

    @staticmethod
    def _clean_json(text: str) -> str:
        """Elimina bloques markdown ```json ... ``` que Gemini a veces agrega."""
        text = text.strip()
        if text.startswith("```"):
            # Quitar primera línea (```json) y última (```)
            lines = text.split("\n")
            lines = [line for line in lines if not line.strip().startswith("```")]
            text = "\n".join(lines)
        return text.strip()

    def _parse_response(self, text: str) -> IntentResult:
        try:
            data = json.loads(self._clean_json(text))
        except json.JSONDecodeError as e:
            raise IntentDetectionError(f"Respuesta de Gemini no es JSON válido: {text[:200]}") from e

        if not data.get("intent_detected"):
            return IntentResult.not_detected(summary=data.get("summary", "Sin intención detectada"))

        product_type_str = data.get("product_type")
        product_type = PRODUCT_TYPE_MAP.get(product_type_str)
        if product_type is None:
            raise IntentDetectionError(f"Tipo de producto no reconocido: {product_type_str}")

        entities_data = data.get("entities", {})
        entities = ExtractedEntities(
            amount=entities_data.get("amount"),
            term=entities_data.get("term"),
            location=entities_data.get("location"),
        )

        return IntentResult.detected(
            product_type=product_type,
            confidence=Confidence(float(data.get("confidence", 0.0))),
            entities=entities,
            summary=data.get("summary", ""),
        )

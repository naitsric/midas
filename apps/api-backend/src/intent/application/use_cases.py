from src.conversation.domain.entities import Conversation
from src.intent.domain.entities import IntentResult
from src.intent.domain.exceptions import IntentDetectionError
from src.intent.domain.ports import IntentDetector


class DetectFinancialIntent:
    def __init__(self, detector: IntentDetector):
        self._detector = detector

    async def execute(self, conversation: Conversation) -> IntentResult:
        if conversation.message_count == 0:
            raise IntentDetectionError("No se puede detectar intención en una conversación sin mensajes")

        return await self._detector.detect(conversation)

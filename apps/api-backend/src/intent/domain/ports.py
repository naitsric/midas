from abc import ABC, abstractmethod

from src.conversation.domain.entities import Conversation
from src.intent.domain.entities import IntentResult


class IntentDetector(ABC):
    @abstractmethod
    async def detect(self, conversation: Conversation) -> IntentResult:
        pass

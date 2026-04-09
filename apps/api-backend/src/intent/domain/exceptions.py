from src.conversation.domain.exceptions import DomainError


class InvalidConfidenceError(DomainError):
    pass


class IntentDetectionError(DomainError):
    pass

from src.conversation.domain.exceptions import DomainError


class InvalidApplicationError(DomainError):
    pass


class ApplicationGenerationError(DomainError):
    pass

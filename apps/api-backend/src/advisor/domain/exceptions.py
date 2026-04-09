from src.conversation.domain.exceptions import DomainError


class InvalidAdvisorError(DomainError):
    pass


class AdvisorNotFoundError(DomainError):
    pass


class AdvisorAuthenticationError(DomainError):
    pass

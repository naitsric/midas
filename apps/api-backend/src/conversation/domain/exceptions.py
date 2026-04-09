class DomainError(Exception):
    pass


class InvalidMessageError(DomainError):
    pass


class EmptyConversationError(DomainError):
    pass

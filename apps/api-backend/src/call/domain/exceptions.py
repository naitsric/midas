class CallError(Exception):
    pass


class InvalidCallStateError(CallError):
    pass


class TranscriptionError(CallError):
    pass

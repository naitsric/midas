from dataclasses import FrozenInstanceError

import pytest

from src.copilot.domain.events import ClientActionEvent


class TestClientActionEvent:
    def test_creates_event_with_action_and_payload(self):
        ev = ClientActionEvent(
            action="create_event",
            payload={"title": "Call Juan", "when_iso": "2026-04-24T10:00:00-05:00", "duration_minutes": 30},
        )
        assert ev.action == "create_event"
        assert ev.payload["title"] == "Call Juan"
        assert ev.payload["duration_minutes"] == 30

    def test_is_immutable(self):
        ev = ClientActionEvent(action="create_event", payload={})
        with pytest.raises(FrozenInstanceError):
            ev.action = "other"  # type: ignore[misc]

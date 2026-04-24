"""Tests del payload builder para client-side tools."""

from src.copilot.infrastructure.adk.tools import (
    CLIENT_SIDE_TOOL_ACTIONS,
    build_client_action_payload,
)


class TestClientSideToolActions:
    def test_create_reminder_maps_to_create_event(self):
        assert CLIENT_SIDE_TOOL_ACTIONS["create_reminder"] == "create_event"


class TestBuildClientActionPayload:
    def test_create_reminder_payload(self):
        payload = build_client_action_payload(
            "create_reminder",
            {"title": "Llamar Juan", "when_iso": "2026-04-24T10:00:00-05:00", "duration_minutes": 30},
        )
        assert payload == {
            "title": "Llamar Juan",
            "when_iso": "2026-04-24T10:00:00-05:00",
            "duration_minutes": 30,
        }

    def test_create_reminder_default_duration_when_missing(self):
        payload = build_client_action_payload(
            "create_reminder",
            {"title": "x", "when_iso": "2026-04-24T10:00:00-05:00"},
        )
        assert payload["duration_minutes"] == 30

    def test_create_reminder_coerces_string_duration(self):
        payload = build_client_action_payload(
            "create_reminder",
            {"title": "x", "when_iso": "2026-04-24T10:00:00-05:00", "duration_minutes": "45"},
        )
        assert payload["duration_minutes"] == 45

    def test_unknown_tool_passthrough(self):
        payload = build_client_action_payload("unknown", {"foo": "bar"})
        assert payload == {"foo": "bar"}

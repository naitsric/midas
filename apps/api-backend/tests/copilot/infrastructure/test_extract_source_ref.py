from src.copilot.domain.value_objects import SourceType
from src.copilot.infrastructure.adk.tools import extract_source_ref


class TestExtractSourceRef:
    def test_list_recent_calls_with_results(self):
        ref = extract_source_ref("list_recent_calls", {"calls": [], "count": 3})
        assert ref is not None
        assert ref.type == SourceType.CALL
        assert "3" in ref.label

    def test_list_recent_calls_empty_returns_none(self):
        assert extract_source_ref("list_recent_calls", {"count": 0}) is None

    def test_get_call_transcript_uses_client_name(self):
        ref = extract_source_ref(
            "get_call_transcript", {"client": "Juan Pérez", "transcript": "..."}
        )
        assert ref is not None
        assert ref.type == SourceType.CALL
        assert "Juan Pérez" in ref.label

    def test_list_applications_returns_application_chip(self):
        ref = extract_source_ref("list_applications", {"count": 2, "applications": []})
        assert ref is not None
        assert ref.type == SourceType.APPLICATION

    def test_get_application_detail_uses_applicant_name(self):
        ref = extract_source_ref(
            "get_application_detail",
            {"applicant": {"full_name": "María López"}, "id": "x"},
        )
        assert ref is not None
        assert ref.type == SourceType.APPLICATION
        assert "María López" in ref.label

    def test_search_conversations_returns_chat_chip(self):
        ref = extract_source_ref("search_conversations", {"count": 5, "conversations": []})
        assert ref is not None
        assert ref.type == SourceType.CHAT
        assert "5" in ref.label

    def test_error_result_returns_none(self):
        assert extract_source_ref("get_call_transcript", {"error": "not found"}) is None

    def test_unknown_tool_returns_none(self):
        assert extract_source_ref("never_existed", {"count": 1}) is None

    def test_non_dict_result_returns_none(self):
        assert extract_source_ref("list_recent_calls", "string") is None

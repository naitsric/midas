from collections.abc import AsyncIterator

from google.cloud.speech_v2 import SpeechAsyncClient
from google.cloud.speech_v2.types import cloud_speech

from src.call.domain.exceptions import TranscriptionError
from src.call.domain.ports import SpeechTranscriber, TranscriptChunk


class GoogleSpeechTranscriber(SpeechTranscriber):
    """Google Cloud Speech-to-Text v2 streaming adapter."""

    def __init__(self, project_id: str, language_codes: list[str] | None = None):
        self._project_id = project_id
        self._language_codes = language_codes or ["es", "en"]

    async def transcribe_stream(self, audio_chunks: AsyncIterator[bytes]) -> AsyncIterator[TranscriptChunk]:
        try:
            client = SpeechAsyncClient()

            recognition_config = cloud_speech.RecognitionConfig(
                explicit_decoding_config=cloud_speech.ExplicitDecodingConfig(
                    encoding=cloud_speech.ExplicitDecodingConfig.AudioEncoding.LINEAR16,
                    sample_rate_hertz=16000,
                    audio_channel_count=1,
                ),
                language_codes=self._language_codes,
                model="long",
                features=cloud_speech.RecognitionFeatures(
                    enable_automatic_punctuation=True,
                ),
            )

            streaming_config = cloud_speech.StreamingRecognitionConfig(
                config=recognition_config,
                streaming_features=cloud_speech.StreamingRecognitionFeatures(
                    interim_results=True,
                ),
            )

            config_request = cloud_speech.StreamingRecognizeRequest(
                recognizer=f"projects/{self._project_id}/locations/global/recognizers/_",
                streaming_config=streaming_config,
            )

            async def request_generator():
                yield config_request
                async for chunk in audio_chunks:
                    yield cloud_speech.StreamingRecognizeRequest(audio=chunk)

            responses = await client.streaming_recognize(requests=request_generator())

            async for response in responses:
                for result in response.results:
                    if result.alternatives:
                        text = result.alternatives[0].transcript
                        if text.strip():
                            yield TranscriptChunk(
                                text=text.strip(),
                                is_final=result.is_final,
                            )
        except Exception as e:
            if isinstance(e, TranscriptionError):
                raise
            raise TranscriptionError(f"Error en transcripción: {e}") from e


class FakeSpeechTranscriber(SpeechTranscriber):
    """Fake para tests y desarrollo local sin Google Cloud."""

    def __init__(self, chunks: list[TranscriptChunk] | None = None):
        self._chunks = chunks or []

    async def transcribe_stream(self, audio_chunks: AsyncIterator[bytes]) -> AsyncIterator[TranscriptChunk]:
        # Consumir el stream de audio (simular procesamiento)
        async for _ in audio_chunks:
            pass
        for chunk in self._chunks:
            yield chunk

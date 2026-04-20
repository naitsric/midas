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


class GeminiSpeechTranscriber(SpeechTranscriber):
    """Transcribe audio usando Gemini. Acumula ~5s de audio PCM, lo envía como WAV."""

    def __init__(self, api_key: str, chunk_seconds: int = 5):
        self._api_key = api_key
        self._chunk_seconds = chunk_seconds
        # PCM 16kHz 16-bit mono = 32000 bytes/sec
        self._bytes_per_chunk = 32000 * chunk_seconds

    @staticmethod
    def _pcm_to_wav(pcm_data: bytes, sample_rate: int = 16000, channels: int = 1, bits: int = 16) -> bytes:
        """Agrega WAV header a datos PCM raw."""
        import struct

        data_size = len(pcm_data)
        header = struct.pack(
            "<4sI4s4sIHHIIHH4sI",
            b"RIFF",
            36 + data_size,
            b"WAVE",
            b"fmt ",
            16,
            1,  # PCM format
            channels,
            sample_rate,
            sample_rate * channels * bits // 8,
            channels * bits // 8,
            bits,
            b"data",
            data_size,
        )
        return header + pcm_data

    async def transcribe_stream(self, audio_chunks: AsyncIterator[bytes]) -> AsyncIterator[TranscriptChunk]:
        import google.generativeai as genai

        genai.configure(api_key=self._api_key)
        model = genai.GenerativeModel("gemini-2.0-flash")

        buffer = bytearray()

        async for chunk in audio_chunks:
            buffer.extend(chunk)

            if len(buffer) >= self._bytes_per_chunk:
                wav_data = self._pcm_to_wav(bytes(buffer))
                buffer.clear()

                try:
                    response = await model.generate_content_async(
                        [
                            {"mime_type": "audio/wav", "data": wav_data},
                            "Transcribe este audio exactamente como se dice, palabra por palabra. "
                            "Solo devuelve la transcripcion, sin explicaciones ni formato adicional. "
                            "Si no hay habla clara, devuelve una cadena vacia.",
                        ],
                    )
                    text = response.text.strip()
                    if text:
                        yield TranscriptChunk(text=text, is_final=False)
                        yield TranscriptChunk(text=text, is_final=True)
                except Exception:
                    pass

        # Procesar audio restante en el buffer
        if len(buffer) > 3200:  # Al menos 0.1s de audio
            wav_data = self._pcm_to_wav(bytes(buffer))
            try:
                response = await model.generate_content_async(
                    [
                        {"mime_type": "audio/wav", "data": wav_data},
                        "Transcribe este audio exactamente como se dice, palabra por palabra. "
                        "Solo devuelve la transcripcion, sin explicaciones ni formato adicional. "
                        "Si no hay habla clara, devuelve una cadena vacia.",
                    ],
                )
                text = response.text.strip()
                if text:
                    yield TranscriptChunk(text=text, is_final=True)
            except Exception:
                pass



"""
Helper para firmar URLs S3 de las grabaciones de llamadas.

El Recording Processor Lambda guarda en `call.recording_url` una URL
pre-firmada con TTL de 1h, pero para playback en la app necesitamos
una URL fresca cada vez que el asesor abre el detalle (la stored
puede llevar días vencida).

Este módulo genera un nuevo presigned GET con TTL configurable.
Si las credenciales / bucket no están disponibles (tests, dev local),
retorna None silenciosamente y el cliente cae al `recording_url`
guardado (que puede o no funcionar).
"""

from __future__ import annotations

import logging
import os
from functools import lru_cache

logger = logging.getLogger("midas.call.s3_signing")

DEFAULT_TTL_SECONDS = 3600  # 1 hora — coincide con el Lambda


@lru_cache(maxsize=1)
def _client():
    try:
        import boto3  # noqa: PLC0415

        region = os.getenv("AWS_DEFAULT_REGION", "us-east-1")
        return boto3.client("s3", region_name=region)
    except Exception as e:  # noqa: BLE001
        logger.warning(f"No se pudo inicializar boto3 S3 client: {e}")
        return None


def presign_recording_url(recording_key: str | None, ttl_seconds: int = DEFAULT_TTL_SECONDS) -> str | None:
    """Genera una URL pre-firmada para descargar el WAV de una grabación.

    Devuelve None si:
    - No hay key (la llamada no fue grabada todavía).
    - No hay bucket configurado (RECORDINGS_BUCKET env var ausente).
    - boto3 falla por credenciales / red.
    """
    if not recording_key:
        return None
    bucket = os.getenv("RECORDINGS_BUCKET")
    if not bucket:
        return None
    s3 = _client()
    if s3 is None:
        return None
    try:
        return s3.generate_presigned_url(
            "get_object",
            Params={"Bucket": bucket, "Key": recording_key},
            ExpiresIn=ttl_seconds,
        )
    except Exception as e:  # noqa: BLE001
        logger.warning(f"presign falló para {recording_key}: {e}")
        return None

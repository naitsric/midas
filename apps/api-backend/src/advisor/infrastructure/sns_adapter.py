import asyncio
import os

import boto3
from botocore.exceptions import ClientError

from src.advisor.domain.ports import VoipPushRegistrar
from src.advisor.domain.value_objects import AdvisorId


class SnsVoipPushRegistrar(VoipPushRegistrar):
    """
    Registra tokens de PushKit como SNS PlatformEndpoints sobre la PlatformApplication APNS_VOIP.

    El ARN de la PlatformApplication se lee desde SSM Parameter Store al instanciar
    el adapter. Esto permite cambiar el ARN sin redeploy del backend.
    """

    def __init__(self, platform_app_arn: str | None = None, region: str | None = None):
        self._region = region or os.environ.get("AWS_DEFAULT_REGION", "us-east-1")
        self._sns = boto3.client("sns", region_name=self._region)
        if platform_app_arn:
            self._platform_app_arn = platform_app_arn
        else:
            self._platform_app_arn = self._load_arn_from_ssm()

    def _load_arn_from_ssm(self) -> str:
        param_name = os.environ.get("SNS_VOIP_PLATFORM_APP_ARN_PARAM")
        if not param_name:
            raise RuntimeError("SNS_VOIP_PLATFORM_APP_ARN_PARAM no configurado")
        ssm = boto3.client("ssm", region_name=self._region)
        resp = ssm.get_parameter(Name=param_name)
        value = resp["Parameter"]["Value"]
        if value.startswith("PLACEHOLDER"):
            raise RuntimeError(
                f"SSM Parameter {param_name} es placeholder. Corre infra/scripts/create-apns-voip-platform.py."
            )
        return value

    async def register_device_token(self, device_token: str, advisor_id: AdvisorId) -> str:
        return await asyncio.to_thread(self._create_endpoint_sync, device_token, advisor_id)

    def _create_endpoint_sync(self, device_token: str, advisor_id: AdvisorId) -> str:
        try:
            resp = self._sns.create_platform_endpoint(
                PlatformApplicationArn=self._platform_app_arn,
                Token=device_token,
                CustomUserData=str(advisor_id.value),
            )
            return resp["EndpointArn"]
        except ClientError as e:
            # SNS retorna InvalidParameter cuando el endpoint ya existe con otro CustomUserData.
            # Extraemos el ARN del error message y lo reusamos (re-enable + re-set attrs).
            msg = str(e)
            if "already exists" in msg:
                import re

                match = re.search(r"Endpoint (arn:aws:sns:\S+) already exists", msg)
                if match:
                    arn = match.group(1)
                    self._sns.set_endpoint_attributes(
                        EndpointArn=arn,
                        Attributes={
                            "Token": device_token,
                            "Enabled": "true",
                            "CustomUserData": str(advisor_id.value),
                        },
                    )
                    return arn
            raise

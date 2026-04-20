#!/usr/bin/env python3
"""
Crea o actualiza el SNS PlatformApplication APNS_VOIP a partir del secret
midas/apns-voip (que debe contener bundle_id, team_id, key_id, private_key).

Guarda el ARN resultante en el SSM Parameter /midas/apns-voip/platform-app-arn.

Requiere AWS_PROFILE configurado (ej: personal). Region us-east-1 por defecto.

Uso:
    AWS_PROFILE=personal python3 infra/scripts/create-apns-voip-platform.py
"""
import json
import os
import sys

import boto3

SECRET_NAME = "midas/apns-voip"
APP_NAME = "midas-voip"
SSM_PARAM = "/midas/apns-voip/platform-app-arn"
REGION = os.environ.get("AWS_REGION", "us-east-1")


def main() -> int:
    session = boto3.Session(region_name=REGION)
    secrets = session.client("secretsmanager")
    sns = session.client("sns")
    ssm = session.client("ssm")

    secret_value = secrets.get_secret_value(SecretId=SECRET_NAME)
    secret = json.loads(secret_value["SecretString"])

    for field in ("bundle_id", "team_id", "key_id", "private_key"):
        if not secret.get(field) or secret[field].startswith("PLACEHOLDER"):
            print(f"ERROR: secret field '{field}' es placeholder. Sube tus credenciales reales primero.", file=sys.stderr)
            return 1

    attributes = {
        "PlatformPrincipal": secret["key_id"],
        "PlatformCredential": secret["private_key"],
        "ApplePlatformTeamID": secret["team_id"],
        "ApplePlatformBundleID": secret["bundle_id"],
    }

    existing_arn = None
    paginator = sns.get_paginator("list_platform_applications")
    for page in paginator.paginate():
        for app in page["PlatformApplications"]:
            if app["PlatformApplicationArn"].endswith(f"/APNS_VOIP/{APP_NAME}"):
                existing_arn = app["PlatformApplicationArn"]
                break
        if existing_arn:
            break

    if existing_arn:
        sns.set_platform_application_attributes(
            PlatformApplicationArn=existing_arn,
            Attributes=attributes,
        )
        arn = existing_arn
        print(f"Updated PlatformApplication: {arn}")
    else:
        resp = sns.create_platform_application(
            Name=APP_NAME,
            Platform="APNS_VOIP",
            Attributes=attributes,
        )
        arn = resp["PlatformApplicationArn"]
        print(f"Created PlatformApplication: {arn}")

    ssm.put_parameter(
        Name=SSM_PARAM,
        Value=arn,
        Type="String",
        Overwrite=True,
    )
    print(f"SSM Parameter {SSM_PARAM} updated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

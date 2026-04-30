package com.midas.voip

/**
 * URL del API Gateway que expone el Lambda de control de llamadas Chime.
 *
 * Hardcoded para coincidir con `iosApp/iOSApp.swift` (línea ~28). Si en el
 * futuro pasamos esto a un build flavor / config remota, hacerlo en ambos
 * lados a la vez.
 */
const val defaultCallControlBaseUrl: String =
    "https://3vv9l6deii.execute-api.us-east-1.amazonaws.com"

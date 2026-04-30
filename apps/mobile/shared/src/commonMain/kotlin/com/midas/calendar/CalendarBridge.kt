package com.midas.calendar

/**
 * Puente a integraciones nativas de calendario.
 *
 * iOS: implementado en Swift con EventKit, registrado en MidasContext
 *      desde el AppDelegate.
 * Android: no implementado en v0.1 (las llamadas fallan con reason="unsupported").
 *
 * Usamos callbacks en lugar de suspend para evitar fricción del interop
 * KMP↔Swift (suspend exporta como completion handler con detalles de
 * threading que complican cuando el caller es un Compose coroutine).
 */
interface CalendarBridge {

    /**
     * Crea un evento en el calendar default del usuario con un alarm
     * automático 5 minutos antes.
     *
     * @param title descripción visible del evento.
     * @param whenIso fecha-hora ISO 8601 con timezone offset.
     * @param durationMinutes duración del evento en minutos.
     * @param onSuccess callback con el identificador del evento creado.
     * @param onError callback con `reason` ("permission_denied" | "invalid_date"
     *               | "store_error" | "unsupported" | "unknown") y un mensaje
     *               humano-legible.
     */
    fun createEvent(
        title: String,
        whenIso: String,
        durationMinutes: Int,
        onSuccess: (eventId: String) -> Unit,
        onError: (reason: String, message: String) -> Unit,
    )
}

object CalendarErrorReason {
    const val PERMISSION_DENIED = "permission_denied"
    const val INVALID_DATE = "invalid_date"
    const val STORE_ERROR = "store_error"
    const val UNSUPPORTED = "unsupported"
    const val UNKNOWN = "unknown"
}

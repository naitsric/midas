package com.midas.applications.export

/**
 * Puente platform-specific para escribir un archivo temporal y abrir el
 * share sheet del sistema (UIActivityViewController iOS / Intent.ACTION_SEND
 * Android).
 *
 * `expect class` con constructor sin argumentos — la implementación de
 * cada plataforma resuelve sus dependencias internamente:
 *   - Android lee el `applicationContext` desde `AndroidContextHolder`
 *     (mismo patrón que `KeyValueStorage`).
 *   - iOS usa `UIApplication.sharedApplication.keyWindow` para presentar
 *     el activity sheet.
 *
 * El bridge NO bloquea — devuelve antes de que el usuario cierre el share
 * sheet. Si el usuario cancela, no llega ningún callback (parámetro
 * `onResult` opcional).
 */
expect class FileShareBridge() {
    /**
     * Escribe `content` (UTF-8) a un archivo temporal y abre el share sheet.
     *
     * @param filename nombre con extensión (ej. `midas-solicitudes-2026-04-30.csv`).
     * @param mimeType MIME ("text/csv", etc.).
     * @param content texto del archivo (incluye BOM si aplica).
     * @param onError callback si la escritura / share falla.
     */
    fun shareTextFile(
        filename: String,
        mimeType: String,
        content: String,
        onError: (String) -> Unit = {},
    )
}

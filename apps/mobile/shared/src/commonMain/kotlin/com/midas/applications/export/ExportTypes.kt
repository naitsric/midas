package com.midas.applications.export

/**
 * Tipos compartidos para la exportación de solicitudes a archivo.
 *
 * Mirror del diseño en `applications-export.jsx`. La lógica de generación
 * vive en [ApplicationsCsvBuilder]; la UI consume estos enums vía
 * `ApplicationsExportSheet`.
 */

enum class ExportColumn(
    val id: String,
    val label: String,
    val critical: Boolean,
    val defaultSelected: Boolean,
) {
    Id("id", "ID", critical = true, defaultSelected = true),
    Applicant("applicant", "Solicitante", critical = true, defaultSelected = true),
    Product("product", "Producto", critical = true, defaultSelected = true),
    Amount("amount", "Monto", critical = true, defaultSelected = true),
    Term("term", "Plazo", critical = false, defaultSelected = true),
    Status("status", "Estado", critical = true, defaultSelected = true),
    Completeness("completeness", "Completitud", critical = false, defaultSelected = true),
    CreatedAt("createdAt", "Creada", critical = false, defaultSelected = true),
    Phone("phone", "Teléfono", critical = false, defaultSelected = false),
    Income("income", "Ingreso mensual", critical = false, defaultSelected = false),
    Employment("employment", "Empleo", critical = false, defaultSelected = false),
    Location("location", "Ubicación", critical = false, defaultSelected = false);

    companion object {
        fun defaultSelection(): Set<ExportColumn> =
            entries.filter { it.defaultSelected }.toSet()

        fun criticalOnly(): Set<ExportColumn> =
            entries.filter { it.critical }.toSet()

        fun all(): Set<ExportColumn> = entries.toSet()
    }
}

enum class ExportRange(val id: String, val label: String) {
    All("all", "Todas"),
    Today("today", "Hoy"),
    Last7Days("7d", "7 días"),
    Last30Days("30d", "30 días"),
    ThisMonth("month", "Este mes"),
}

/**
 * Formatos de exportación. CSV es el único soportado en v1; XLSX queda
 * declarado para que la UI lo muestre como "Próximamente" (cuando se
 * implemente, agregar la rama en [ApplicationsCsvBuilder]).
 */
enum class ExportFormat(val id: String, val extension: String, val mimeType: String) {
    Csv("csv", "csv", "text/csv"),
    Xlsx(
        "xlsx",
        "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ),
}

/**
 * Resultado de [ApplicationsCsvBuilder.build] — contenido del archivo +
 * metadata para la UI (filas y bytes para el preview, filename sugerido).
 */
data class ExportResult(
    val filename: String,
    val mimeType: String,
    val content: String,
    val rowCount: Int,
    val byteSize: Int,
)

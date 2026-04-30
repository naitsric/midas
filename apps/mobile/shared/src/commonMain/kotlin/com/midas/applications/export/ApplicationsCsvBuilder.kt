package com.midas.applications.export

import com.midas.domain.model.CreditApplication

/**
 * Pure-function builder. Toma una lista de [CreditApplication] + selección
 * de columnas y produce un [ExportResult] con el CSV listo para escribir
 * a archivo y compartir.
 *
 * Contrato:
 * - Header con los labels de las columnas (en el mismo orden que el enum).
 * - UTF-8 con BOM para que Excel detecte acentos correctamente.
 * - Separador `,` con escapado de comillas / saltos de línea estilo RFC 4180.
 * - Líneas terminadas en `\n` (Windows / mac aceptan, Excel también).
 *
 * El estimado de bytes es length del string en UTF-8 (aproximado: 1 byte
 * por char ASCII + overhead BOM). No usamos `toByteArray` porque eso es
 * platform-specific y aquí queremos commonMain puro.
 */
object ApplicationsCsvBuilder {

    fun build(
        apps: List<CreditApplication>,
        columns: Set<ExportColumn>,
        filenameStamp: String,
    ): ExportResult {
        val orderedCols = ExportColumn.entries.filter { it in columns }
        val sb = StringBuilder()
        sb.append('﻿')  // UTF-8 BOM — para que Excel detecte acentos
        sb.append(orderedCols.joinToString(",") { csvEscape(it.label) })
        sb.append('\n')
        for (app in apps) {
            sb.append(orderedCols.joinToString(",") { col -> csvEscape(valueFor(app, col)) })
            sb.append('\n')
        }
        val content = sb.toString()
        return ExportResult(
            filename = "midas-solicitudes-$filenameStamp.csv",
            mimeType = ExportFormat.Csv.mimeType,
            content = content,
            rowCount = apps.size,
            byteSize = content.length,
        )
    }

    /**
     * Filtra aplicaciones por rango de fecha basándose en el campo
     * `created_at` (ISO 8601). Si la fecha viene en formato no estándar
     * o vacío, la app pasa el filtro (mejor inclusivo que perder data
     * en exportación).
     */
    fun filterByRange(
        apps: List<CreditApplication>,
        range: ExportRange,
        nowIsoDate: String,
    ): List<CreditApplication> {
        if (range == ExportRange.All) return apps
        val now = parseIsoDate(nowIsoDate) ?: return apps
        val cutoff: SimpleDate = when (range) {
            ExportRange.Today -> now
            ExportRange.Last7Days -> now.minusDays(7)
            ExportRange.Last30Days -> now.minusDays(30)
            ExportRange.ThisMonth -> SimpleDate(now.year, now.month, 1)
            ExportRange.All -> return apps
        }
        return apps.filter { app ->
            val created = parseIsoDate(app.createdAt) ?: return@filter true
            created >= cutoff
        }
    }

    private fun valueFor(app: CreditApplication, col: ExportColumn): String = when (col) {
        ExportColumn.Id -> app.id.uppercase()
        ExportColumn.Applicant -> app.applicant.fullName
        ExportColumn.Product -> app.productRequest.productLabel
            ?: app.productRequest.productType
            ?: ""
        ExportColumn.Amount -> app.productRequest.amount.orEmpty()
        ExportColumn.Term -> app.productRequest.term.orEmpty()
        ExportColumn.Status -> app.statusLabel ?: app.status
        ExportColumn.Completeness -> {
            val pct = app.applicant.completeness?.let { (it * 100).toInt() }
            pct?.let { "$it%" } ?: ""
        }
        ExportColumn.CreatedAt -> app.createdAt
        ExportColumn.Phone -> app.applicant.phone.orEmpty()
        ExportColumn.Income -> app.applicant.estimatedIncome.orEmpty()
        ExportColumn.Employment -> app.applicant.employmentType.orEmpty()
        ExportColumn.Location -> app.productRequest.location.orEmpty()
    }

    private fun csvEscape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}

// ── Mini ISO-8601 date parsing — sin depender de java.time para que
// commonMain quede puro. Sólo necesitamos comparación de fechas, no
// timezones ni horas (el campo created_at viene como "2026-04-30T...").

private data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {
    override fun compareTo(other: SimpleDate): Int {
        val y = year.compareTo(other.year)
        if (y != 0) return y
        val m = month.compareTo(other.month)
        if (m != 0) return m
        return day.compareTo(other.day)
    }

    fun minusDays(n: Int): SimpleDate {
        // Approx: bajar `n` días tratando los meses como 30. Suficiente
        // para nuestros rangos cortos (≤30 días). Si necesitamos precisión
        // mayor, integrar kotlinx-datetime.
        var d = day - n
        var m = month
        var y = year
        while (d < 1) {
            m -= 1
            if (m < 1) { m = 12; y -= 1 }
            d += daysInMonth(y, m)
        }
        return SimpleDate(y, m, d)
    }
}

private fun parseIsoDate(iso: String): SimpleDate? {
    if (iso.length < 10) return null
    val y = iso.substring(0, 4).toIntOrNull() ?: return null
    val m = iso.substring(5, 7).toIntOrNull() ?: return null
    val d = iso.substring(8, 10).toIntOrNull() ?: return null
    return SimpleDate(y, m, d)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

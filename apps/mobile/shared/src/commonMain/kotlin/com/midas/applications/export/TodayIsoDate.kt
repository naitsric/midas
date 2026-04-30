package com.midas.applications.export

/**
 * Devuelve la fecha local actual en formato `yyyy-MM-dd`. Necesitamos esto
 * para nombrar el archivo (`midas-solicitudes-2026-04-30.csv`) y para
 * filtrar por rango de fechas en [ApplicationsCsvBuilder.filterByRange].
 *
 * No agregamos `kotlinx-datetime` solo para esto — implementación trivial
 * por plataforma con `java.time.LocalDate` (Android) / `NSDateFormatter`
 * (iOS).
 */
expect fun todayIsoDate(): String

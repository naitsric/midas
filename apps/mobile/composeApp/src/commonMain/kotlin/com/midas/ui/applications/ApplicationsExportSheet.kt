package com.midas.ui.applications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.midas.applications.export.ApplicationsCsvBuilder
import com.midas.applications.export.ExportColumn
import com.midas.applications.export.ExportFormat
import com.midas.applications.export.ExportRange
import com.midas.applications.export.FileShareBridge
import com.midas.domain.model.CreditApplication
import com.midas.ui.theme.LocalMidasColors

/**
 * Bottom sheet de exportación de solicitudes a CSV — equivalente Compose del
 * `ExportSheet` en `applications-export.jsx`.
 *
 * Estructura del diseño:
 *   01 / Qué incluir   — radio: filtro actual vs. todas
 *   02 / Rango fechas  — pills (Todas / Hoy / 7d / 30d / Este mes)
 *   03 / Formato       — cards CSV (activo) / XLSX (disabled, "Próximamente")
 *   04 / Columnas      — accordion con presets + checkboxes
 *
 * Footer fijo: preview "{filas} · {col} · ~{KB} · .CSV" + CTA verde con
 * 3 estados (idle / loading / done).
 *
 * v1: solo CSV (XLSX requiere implementar generador de zip XML cross-platform
 * o lib que no tengo en mobile — queda como follow-up).
 */
internal enum class ExportScope { CurrentFilter, All }

private enum class ExportStage { Idle, Loading, Done }

@Composable
fun ApplicationsExportSheet(
    visible: Boolean,
    allApplications: List<CreditApplication>,
    filteredApplications: List<CreditApplication>,
    currentFilterLabel: String,
    onDismiss: () -> Unit,
    fileShareBridge: FileShareBridge = remember { FileShareBridge() },
    nowIsoDate: String,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ExportSheetContent(
            allApplications = allApplications,
            filteredApplications = filteredApplications,
            currentFilterLabel = currentFilterLabel,
            onDismiss = onDismiss,
            fileShareBridge = fileShareBridge,
            nowIsoDate = nowIsoDate,
        )
    }
}

@Composable
private fun ExportSheetContent(
    allApplications: List<CreditApplication>,
    filteredApplications: List<CreditApplication>,
    currentFilterLabel: String,
    onDismiss: () -> Unit,
    fileShareBridge: FileShareBridge,
    nowIsoDate: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent

    val isFiltered = filteredApplications.size != allApplications.size

    var scope by remember(isFiltered) {
        mutableStateOf(if (isFiltered) ExportScope.CurrentFilter else ExportScope.All)
    }
    var range by remember { mutableStateOf(ExportRange.All) }
    var format by remember { mutableStateOf(ExportFormat.Csv) }
    var columns by remember { mutableStateOf(ExportColumn.defaultSelection()) }
    var columnsExpanded by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(ExportStage.Idle) }
    var resultFilename by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val baseApps = if (scope == ExportScope.CurrentFilter) filteredApplications else allApplications
    val rangedApps = remember(baseApps, range, nowIsoDate) {
        ApplicationsCsvBuilder.filterByRange(baseApps, range, nowIsoDate)
    }
    val rowCount = rangedApps.size
    val colCount = columns.size
    val canDownload = colCount > 0 && rowCount > 0 && stage == ExportStage.Idle &&
        format == ExportFormat.Csv

    // Auto-close 1.8s after success
    LaunchedEffect(stage) {
        if (stage == ExportStage.Done) {
            kotlinx.coroutines.delay(1800)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = stage != ExportStage.Loading) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(if (colors.isDark) Color(0xFF1A1A1A) else Color.White)
                .border(
                    1.dp,
                    colors.cardBorder,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .clickable(enabled = false) { /* swallow taps so backdrop click doesn't dismiss */ }
                .heightIn(max = 700.dp),
        ) {
            Grabber(colors.isDark)
            ExportSheetHeader(
                stage = stage,
                onClose = { if (stage != ExportStage.Loading) onDismiss() },
            )

            // Body
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
            ) {
                Section(num = "01", title = "Qué incluir") {
                    ScopeRow(
                        active = scope == ExportScope.CurrentFilter,
                        title = "Filtro actual · $currentFilterLabel",
                        subtitle = "${filteredApplications.size} solicitud" +
                            if (filteredApplications.size != 1) "es" else "",
                        disabled = !isFiltered,
                        hint = if (!isFiltered) "Equivale a todas" else null,
                        onClick = { scope = ExportScope.CurrentFilter },
                    )
                    ScopeRow(
                        active = scope == ExportScope.All,
                        title = "Todas las solicitudes",
                        subtitle = "${allApplications.size} en total",
                        disabled = false,
                        hint = null,
                        onClick = { scope = ExportScope.All },
                    )
                }

                Section(num = "02", title = "Rango de fechas") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RangePill(ExportRange.All, range == ExportRange.All) { range = ExportRange.All }
                            RangePill(ExportRange.Today, range == ExportRange.Today) { range = ExportRange.Today }
                            RangePill(ExportRange.Last7Days, range == ExportRange.Last7Days) { range = ExportRange.Last7Days }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RangePill(ExportRange.Last30Days, range == ExportRange.Last30Days) { range = ExportRange.Last30Days }
                            RangePill(ExportRange.ThisMonth, range == ExportRange.ThisMonth) { range = ExportRange.ThisMonth }
                        }
                    }
                }

                Section(num = "03", title = "Formato") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormatCard(
                            ext = "CSV",
                            title = "CSV",
                            subtitle = "Texto plano, importable en cualquier sistema",
                            active = format == ExportFormat.Csv,
                            disabled = false,
                            onClick = { format = ExportFormat.Csv },
                            modifier = Modifier.weight(1f),
                        )
                        FormatCard(
                            ext = "XLSX",
                            title = "Excel",
                            subtitle = "Próximamente",
                            active = format == ExportFormat.Xlsx,
                            disabled = true,
                            onClick = { /* disabled */ },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Section(num = "04", title = "Columnas") {
                    ColumnsAccordion(
                        expanded = columnsExpanded,
                        selectedCount = colCount,
                        onToggle = { columnsExpanded = !columnsExpanded },
                    )
                    AnimatedVisibility(
                        visible = columnsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PresetButton("Recomendadas") { columns = ExportColumn.defaultSelection() }
                                PresetButton("Solo esenciales") { columns = ExportColumn.criticalOnly() }
                                PresetButton("Todas") { columns = ExportColumn.all() }
                            }
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp)),
                            ) {
                                ExportColumn.entries.forEachIndexed { i, c ->
                                    ColumnRow(
                                        column = c,
                                        checked = c in columns,
                                        onToggle = {
                                            columns = if (c in columns) columns - c else columns + c
                                        },
                                        isLast = i == ExportColumn.entries.size - 1,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SecondaryEmailHint()

                if (errorMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = colors.statusNegative,
                        fontSize = 11.sp,
                    )
                }
            }

            // Footer
            ExportSheetFooter(
                rowCount = rowCount,
                colCount = colCount,
                format = format,
                stage = stage,
                filename = resultFilename,
                canDownload = canDownload,
                onDownload = {
                    if (!canDownload) return@ExportSheetFooter
                    stage = ExportStage.Loading
                    errorMessage = null
                    val result = ApplicationsCsvBuilder.build(
                        apps = rangedApps,
                        columns = columns,
                        filenameStamp = nowIsoDate,
                    )
                    fileShareBridge.shareTextFile(
                        filename = result.filename,
                        mimeType = result.mimeType,
                        content = result.content,
                        onError = {
                            errorMessage = it
                            stage = ExportStage.Idle
                        },
                    )
                    resultFilename = result.filename
                    stage = ExportStage.Done
                },
            )
        }
    }
}

// ─────────────────────────── Sub-components ───────────────────────────

@Composable
private fun Grabber(isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.18f)
                    else Color.Black.copy(alpha = 0.18f),
                ),
        )
    }
}

@Composable
private fun ExportSheetHeader(stage: ExportStage, onClose: () -> Unit) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "EXPORTAR",
                color = colors.primaryAccent,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Descargar solicitudes",
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
        }
        val closeBg = if (colors.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(closeBg)
                .clickable(enabled = stage != ExportStage.Loading) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = colors.textPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun Section(num: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMidasColors.current
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(modifier = Modifier.padding(bottom = 10.dp)) {
            Text(
                text = "$num /",
                color = colors.primaryAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title.uppercase(),
                color = colors.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
            )
        }
        content()
    }
}

@Composable
private fun ScopeRow(
    active: Boolean,
    title: String,
    subtitle: String,
    disabled: Boolean,
    hint: String?,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = when {
        active -> if (colors.isDark) accent.copy(alpha = 0.08f) else accent.copy(alpha = 0.10f)
        else -> if (colors.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
    }
    val border = if (active) accent else colors.cardBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = !disabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (disabled) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (active) accent else colors.muted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint ?: subtitle,
                color = colors.muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun RangePill(range: ExportRange, active: Boolean, onClick: () -> Unit) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = when {
        active -> accent
        colors.isDark -> Color.White.copy(alpha = 0.04f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val textColor = if (active) colors.primaryAccentOn else colors.textPrimary
    val borderColor = if (active) accent else colors.cardBorder
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = range.label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun FormatCard(
    ext: String,
    title: String,
    subtitle: String,
    active: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = when {
        active -> if (colors.isDark) accent.copy(alpha = 0.08f) else accent.copy(alpha = 0.10f)
        colors.isDark -> Color.White.copy(alpha = 0.03f)
        else -> Color.Black.copy(alpha = 0.03f)
    }
    val border = if (active) accent else colors.cardBorder
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = !disabled) { onClick() }
            .padding(12.dp)
            .alpha(if (disabled) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tagBg = if (active) accent
                else if (colors.isDark) Color.White.copy(alpha = 0.06f)
                else Color.Black.copy(alpha = 0.06f)
            val tagColor = if (active) colors.primaryAccentOn else colors.muted
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tagBg)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = ".$ext",
                    color = tagColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (active) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = colors.muted,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun ColumnsAccordion(expanded: Boolean, selectedCount: Int, onToggle: () -> Unit) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$selectedCount columna" + if (selectedCount != 1) "s incluidas" else " incluida",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (expanded) "Toca para ocultar" else "Personalizar",
                color = colors.muted,
                fontSize = 11.sp,
            )
        }
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    val colors = LocalMidasColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(7.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ColumnRow(
    column: ExportColumn,
    checked: Boolean,
    onToggle: () -> Unit,
    isLast: Boolean,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) accent else Color.Transparent)
                .border(
                    1.6.dp,
                    if (checked) accent else colors.muted,
                    RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.primaryAccentOn,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = column.label,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (column.critical) {
            val critBg = if (colors.isDark) accent.copy(alpha = 0.10f) else accent.copy(alpha = 0.12f)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(critBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "ESENCIAL",
                    color = accent,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
    if (!isLast) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.cardBorder),
        )
    }
}

@Composable
private fun SecondaryEmailHint() {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                colors.cardBorder,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Email,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Comparte el archivo desde el sistema",
            color = colors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ExportSheetFooter(
    rowCount: Int,
    colCount: Int,
    format: ExportFormat,
    stage: ExportStage,
    filename: String,
    canDownload: Boolean,
    onDownload: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val sizeKb = ((rowCount * colCount * 16) / 1024).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (colors.isDark) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.02f))
            .border(
                width = 1.dp,
                color = colors.cardBorder,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$rowCount fila" + (if (rowCount != 1) "s" else "") +
                    " · $colCount col · ~${sizeKb} KB",
                color = colors.muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = ".${format.extension.uppercase()}",
                color = colors.muted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
            )
        }
        val ctaBg = when {
            stage == ExportStage.Done -> colors.primaryAccent
            canDownload -> colors.primaryAccent
            colors.isDark -> Color.White.copy(alpha = 0.08f)
            else -> Color.Black.copy(alpha = 0.06f)
        }
        val ctaText = if (canDownload || stage != ExportStage.Idle) colors.primaryAccentOn else colors.muted
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ctaBg)
                .clickable(enabled = canDownload) { onDownload() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (stage) {
                ExportStage.Idle -> {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = ctaText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Descargar",
                        color = ctaText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                    )
                }
                ExportStage.Loading -> {
                    CircularProgressIndicator(
                        color = colors.primaryAccentOn,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Generando…",
                        color = colors.primaryAccentOn,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ExportStage.Done -> {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.primaryAccentOn,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Descargado · $filename",
                        color = colors.primaryAccentOn,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


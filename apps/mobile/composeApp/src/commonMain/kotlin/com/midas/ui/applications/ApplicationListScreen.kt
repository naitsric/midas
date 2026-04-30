package com.midas.ui.applications

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.applications.export.todayIsoDate
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.ApplicantData
import com.midas.domain.model.CreditApplication
import com.midas.domain.model.ProductRequest
import com.midas.ui.components.ArrowBackGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasBlue
import com.midas.ui.theme.MidasOrange
import com.midas.ui.theme.MidasPurple
import kotlinx.coroutines.launch

private enum class AppFilter { All, Drafts, Submitted, Review }

@Composable
fun ApplicationListScreen(apiClient: MidasApiClient) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var applications by remember { mutableStateOf<List<CreditApplication>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<CreditApplication?>(null) }
    var filter by remember { mutableStateOf(AppFilter.All) }
    var exportSheetVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                applications = apiClient.listApplications()
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    if (selectedApp != null) {
        ApplicationDetailScreen(app = selectedApp!!, onBack = { selectedApp = null })
        return
    }

    val drafts = applications.count { it.status.equals("draft", ignoreCase = true) }
    val submitted = applications.count { it.status.equals("submitted", ignoreCase = true) }
    val pipelineTotal = applications
        .filter { !it.status.equals("rejected", ignoreCase = true) }
        .sumOf { parseAmount(it.productRequest.amount) }

    val filtered = when (filter) {
        AppFilter.All -> applications
        AppFilter.Drafts -> applications.filter { it.status.equals("draft", ignoreCase = true) }
        AppFilter.Submitted -> applications.filter { it.status.equals("submitted", ignoreCase = true) }
        AppFilter.Review -> applications.filter { it.status.equals("review", ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            // Header row: pill + title + plus button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SyncPill(label = s.appsSyncPill)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s.applicationsTitle,
                        color = colors.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }
                // Header actions: download (⤓) + create (+) — paralelos.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val downloadBg = if (colors.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(downloadBg)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                            .clickable(enabled = applications.isNotEmpty()) {
                                exportSheetVisible = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = s.appsDownload,
                            tint = if (applications.isNotEmpty()) colors.textPrimary else colors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.primaryAccentOn,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Intel chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IntelChip(
                    n = formatPipeline(pipelineTotal),
                    label = s.appsStatPipeline,
                    accent = colors.primaryAccent,
                    modifier = Modifier.weight(1.2f),
                )
                IntelChip(
                    n = drafts.toString(),
                    label = s.appsStatDrafts,
                    accent = MidasOrange,
                    modifier = Modifier.weight(1f),
                )
                IntelChip(
                    n = submitted.toString(),
                    label = s.appsStatSubmitted,
                    accent = MidasPurple,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Filter pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterPill(
                    label = s.appsFilterAll, count = applications.size,
                    selected = filter == AppFilter.All,
                    accent = colors.primaryAccent,
                    onClick = { filter = AppFilter.All },
                )
                FilterPill(
                    label = s.appsFilterDrafts, count = drafts,
                    selected = filter == AppFilter.Drafts,
                    accent = MidasOrange,
                    onClick = { filter = AppFilter.Drafts },
                )
                FilterPill(
                    label = s.appsFilterSubmitted, count = submitted,
                    selected = filter == AppFilter.Submitted,
                    accent = colors.primaryAccent,
                    onClick = { filter = AppFilter.Submitted },
                )
                FilterPill(
                    label = s.appsFilterReview, count = null,
                    selected = filter == AppFilter.Review,
                    accent = MidasBlue,
                    onClick = { filter = AppFilter.Review },
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    }

                    applications.isEmpty() -> Text(
                        text = s.applicationsEmpty,
                        color = colors.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    )

                    filtered.isEmpty() -> Text(
                        text = s.appsEmptyFilter,
                        color = colors.muted,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                    )

                    else -> {
                        filtered.forEachIndexed { index, app ->
                            AppRow(
                                app = app,
                                accent = avatarColorFor(index),
                                onClick = { selectedApp = app },
                            )
                        }
                        Spacer(Modifier.height(60.dp))
                    }
                }
            }
        }

        ApplicationsExportSheet(
            visible = exportSheetVisible,
            allApplications = applications,
            filteredApplications = filtered,
            currentFilterLabel = when (filter) {
                AppFilter.All -> s.appsFilterAll
                AppFilter.Drafts -> s.appsFilterDrafts
                AppFilter.Submitted -> s.appsFilterSubmitted
                AppFilter.Review -> s.appsFilterReview
            },
            onDismiss = { exportSheetVisible = false },
            nowIsoDate = remember { todayIsoDate() },
        )
    }
}

// ─────────────────────────── Sync pill ───────────────────────────

@Composable
private fun SyncPill(label: String) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.statusPositive),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ─────────────────────────── Intel chip ───────────────────────────

@Composable
private fun IntelChip(
    n: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = n,
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 22.sp,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
    }
}

// ─────────────────────────── Filter pill ───────────────────────────

@Composable
private fun FilterPill(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val bg = if (selected) accent.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (selected) accent.copy(alpha = 0.45f) else colors.cardBorder
    val contentColor = if (selected) accent else colors.muted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                color = contentColor.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ─────────────────────────── App row ───────────────────────────

@Composable
private fun AppRow(
    app: CreditApplication,
    accent: Color,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val statusC = statusColor(app.status, colors.primaryAccent)
    val product = app.productRequest.productLabel
        ?: app.productRequest.productType
        ?: "—"
    val amount = app.productRequest.amount ?: "—"
    val term = app.productRequest.term ?: "—"
    val completeness = (app.applicant.completeness ?: 0.0).coerceIn(0.0, 1.0)
    val confidence = (completeness * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top row: avatar + name/product + amount/term
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            0f to accent,
                            1f to accent.copy(alpha = 0.65f),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsOf(app.applicant.fullName),
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.applicant.fullName,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = product,
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amount,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = term,
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        // Completeness bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (LocalMidasColors.current.isDark) Color.White.copy(alpha = 0.06f)
                        else Color.Black.copy(alpha = 0.06f),
                    ),
            ) {
                val barColor = when {
                    completeness >= 0.9 -> colors.primaryAccent
                    completeness >= 0.7 -> MidasOrange
                    else -> colors.statusNegative
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(completeness.toFloat())
                        .background(barColor),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$confidence%",
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Meta row
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                label = (app.statusLabel ?: app.status).uppercase(),
                color = statusC,
            )
            Spacer(Modifier.width(6.dp))
            AiChip(
                accent = colors.primaryAccent,
                label = "${s.appsAiPrefix} $confidence%",
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatRelativeDate(app.createdAt),
                color = colors.muted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun AiChip(accent: Color, label: String) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.04f)
                else Color.Black.copy(alpha = 0.04f),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(8.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = colors.muted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ─────────────────────────── Detail ───────────────────────────

@Composable
private fun ApplicationDetailScreen(app: CreditApplication, onBack: () -> Unit) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val statusC = statusColor(app.status, colors.primaryAccent)
    val completeness = (app.applicant.completeness ?: 0.0).coerceIn(0.0, 1.0)
    val confidence = (completeness * 100).toInt()

    val fields = remember(app) { extractFields(app) }
    val filled = fields.filter { it.value != null }
    val missing = fields.filter { it.value == null }
    var expandedField by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(
                statusLabel = (app.statusLabel ?: app.status).uppercase(),
                statusColor = statusC,
                appId = app.id,
                applicantName = app.applicant.fullName,
                onBack = onBack,
                sendLabel = s.appsSendButton,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                HeroCard(
                    chipPrefix = s.appsHeroChip,
                    chipConfidenceLabel = s.appsHeroConfidence,
                    confidence = confidence,
                    productType = app.productRequest.productLabel
                        ?: app.productRequest.productType
                        ?: "—",
                    amountLabel = s.appsMetricAmount,
                    amount = app.productRequest.amount ?: "—",
                    termLabel = s.appsMetricTerm,
                    term = app.productRequest.term ?: "—",
                    rateLabel = s.appsMetricRate,
                    rate = "—",
                )

                Spacer(Modifier.height(20.dp))

                CompletenessSection(
                    sectionTitle = s.appsSectionCompleteness,
                    completeness = completeness,
                    missingCount = missing.size,
                    footerMissing = s.appsCompletenessFooter,
                    footerReady = s.appsCompletenessReady,
                )

                Spacer(Modifier.height(20.dp))

                NumberedHeader(number = "02", title = s.appsSectionExtracted)

                Spacer(Modifier.height(10.dp))
                FilledFieldsCard(
                    filled = filled,
                    expandedField = expandedField,
                    onToggle = { label ->
                        expandedField = if (expandedField == label) null else label
                    },
                    sourceTrace = s.appsSourceTrace,
                )

                if (missing.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    MissingFieldsSection(
                        title = s.appsRequiresManual,
                        missing = missing,
                        addLabel = s.appsAddAction,
                    )
                }

                Spacer(Modifier.height(20.dp))

                NumberedHeader(number = "03", title = s.appsSectionSummary)

                Spacer(Modifier.height(10.dp))
                SummaryCard(text = app.conversationSummary)

                Spacer(Modifier.height(10.dp))
                SourceFooter(prefix = s.appsSourceLabel, body = sourceFor(app))

                if (!app.rejectionReason.isNullOrBlank()) {
                    Spacer(Modifier.height(20.dp))
                    NumberedHeader(number = "!", title = s.applicationRejectionReason)
                    Spacer(Modifier.height(10.dp))
                    RejectionCard(reason = app.rejectionReason!!)
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    statusLabel: String,
    statusColor: Color,
    appId: String,
    applicantName: String,
    onBack: () -> Unit,
    sendLabel: String,
) {
    val colors = LocalMidasColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.04f)
                        else Color.Black.copy(alpha = 0.04f),
                    )
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                ArrowBackGlyph(tint = colors.textPrimary, size = 14.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$statusLabel · ${appId.take(6).uppercase()}",
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = applicantName,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryAccent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = sendLabel,
                    color = colors.primaryAccentOn,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.cardBorder),
    )
}

@Composable
private fun HeroCard(
    chipPrefix: String,
    chipConfidenceLabel: String,
    confidence: Int,
    productType: String,
    amountLabel: String,
    amount: String,
    termLabel: String,
    term: String,
    rateLabel: String,
    rate: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.10f),
                    1f to accent.copy(alpha = 0.03f),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.27f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$chipPrefix · $confidence% $chipConfidenceLabel",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = productType,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroMetric(label = amountLabel, value = amount, modifier = Modifier.weight(1f))
            HeroMetric(label = termLabel, value = term, modifier = Modifier.weight(1f))
            HeroMetric(label = rateLabel, value = rate, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalMidasColors.current
    Column(modifier = modifier) {
        Text(
            text = label,
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CompletenessSection(
    sectionTitle: String,
    completeness: Double,
    missingCount: Int,
    footerMissing: String,
    footerReady: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            NumberedHeader(number = "01", title = sectionTitle)
            Text(
                text = "${(completeness * 100).toInt()}%",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.06f)
                    else Color.Black.copy(alpha = 0.06f),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(completeness.toFloat())
                    .background(
                        Brush.horizontalGradient(
                            0f to accent,
                            1f to accent.copy(alpha = 0.7f),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (missingCount > 0) footerMissing.replace("%d", missingCount.toString())
            else footerReady,
            color = colors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NumberedHeader(number: String, title: String) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$number /",
            color = colors.primaryAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
    }
}

private data class ExtractedField(
    val label: String,
    val value: String?,
    val confidence: Double,
    val source: String,
)

@Composable
private fun FilledFieldsCard(
    filled: List<ExtractedField>,
    expandedField: String?,
    onToggle: (String) -> Unit,
    sourceTrace: String,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .animateContentSize(),
    ) {
        filled.forEachIndexed { index, field ->
            FieldRow(
                field = field,
                expanded = expandedField == field.label,
                onToggle = { onToggle(field.label) },
                sourceTrace = sourceTrace,
                isLast = index == filled.size - 1,
            )
        }
    }
}

@Composable
private fun FieldRow(
    field: ExtractedField,
    expanded: Boolean,
    onToggle: () -> Unit,
    sourceTrace: String,
    isLast: Boolean,
) {
    val colors = LocalMidasColors.current
    val confColor = when {
        field.confidence >= 0.9 -> colors.primaryAccent
        field.confidence >= 0.75 -> MidasOrange
        else -> colors.statusNegative
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = field.label.uppercase(),
                    color = colors.muted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.0.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = field.value ?: "—",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(confColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "${(field.confidence * 100).toInt()}%",
                    color = confColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    text = "▎",
                    color = colors.primaryAccent,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = sourceTrace,
                        color = colors.primaryAccent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = field.source,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 16.sp,
                    )
                }
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
private fun MissingFieldsSection(
    title: String,
    missing: List<ExtractedField>,
    addLabel: String,
) {
    val colors = LocalMidasColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MidasOrange,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = MidasOrange,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MidasOrange.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
        ) {
            missing.forEachIndexed { index, field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidasOrange.copy(alpha = 0.04f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = field.label,
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = field.source,
                            color = colors.muted,
                            fontSize = 10.5.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, MidasOrange.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .clickable {}
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = addLabel,
                            color = MidasOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
                if (index < missing.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MidasOrange.copy(alpha = 0.20f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(text: String) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = text.ifBlank { "—" },
            color = colors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun SourceFooter(prefix: String, body: String) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$prefix · ${body.uppercase()}",
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RejectionCard(reason: String) {
    val colors = LocalMidasColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.statusNegative.copy(alpha = 0.10f))
            .border(1.dp, colors.statusNegative.copy(alpha = 0.33f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = reason,
            color = colors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}

// ─────────────────────────── helpers ───────────────────────────

private val avatarPalette = listOf(
    Color(0xFF00E676),
    Color(0xFFFF9800),
    Color(0xFFB388FF),
    Color(0xFF42A5F5),
)

private fun avatarColorFor(index: Int): Color = avatarPalette[index % avatarPalette.size]

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

@Suppress("FunctionName")
private fun statusColor(status: String, defaultGreen: Color): Color = when (status.lowercase()) {
    "draft" -> MidasOrange
    "submitted" -> defaultGreen
    "review", "in_review" -> MidasBlue
    "rejected" -> Color(0xFFEF5350)
    else -> Color(0xFF9E9E9E)
}

/**
 * Parses amount strings tolerantly. Handles all of:
 *   "300,000,000"   → 300_000_000
 *   "850 millones"  → 850_000_000
 *   "1.5M" / "1.5MM" / "1.5 millones" → 1_500_000
 *   "$2,500"        → 2_500
 *   "150 mil"       → 150_000
 * The LLM emits these in different shapes depending on the conversation —
 * the parser has to be forgiving so the pipeline total isn't off by 1000x.
 */
private fun parseAmount(raw: String?): Double {
    if (raw.isNullOrBlank()) return 0.0
    val lower = raw.lowercase()

    val multiplier = when {
        // millones / millions / trailing "M" or "MM" (e.g. "1.5M", "1.5MM")
        Regex("""\bmillon(es)?\b|\bmillions?\b|\d\s*mm?\b""").containsMatchIn(lower) -> 1_000_000.0
        // mil / thousand / trailing "K" (e.g. "200K")
        Regex("""\bmil\b|\bthousand\b|\d\s*k\b""").containsMatchIn(lower) -> 1_000.0
        else -> 1.0
    }

    // Strip commas (US thousand sep) — what's left should be digits + at most one dot.
    val numeric = lower.replace(",", "").filter { it.isDigit() || it == '.' }
    val value = numeric.toDoubleOrNull() ?: 0.0
    return value * multiplier
}

private fun formatPipeline(amount: Double): String = when {
    amount >= 1_000_000_000 -> "$${"%.1f".format1(amount / 1_000_000_000)}B"
    amount >= 1_000_000 -> "$${"%.1f".format1(amount / 1_000_000)}M"
    amount >= 1_000 -> "$${"%.0f".format1(amount / 1_000)}K"
    amount > 0 -> "$${"%.0f".format1(amount)}"
    else -> "—"
}

// Tiny KMP-friendly format helper (no java.util.Locale on iOS).
private fun String.format1(value: Double): String {
    // %.1f and %.0f support manual implementation here:
    return when (this) {
        "%.1f" -> {
            val rounded = (value * 10).toLong()
            val whole = rounded / 10
            val tenth = (rounded % 10).let { if (it < 0) -it else it }
            "$whole.$tenth"
        }
        "%.0f" -> value.toLong().toString()
        else -> value.toString()
    }
}

private fun formatRelativeDate(iso: String): String {
    val date = iso.substringBefore('T').takeIf { it.isNotEmpty() } ?: return "—"
    return date
}

private fun extractFields(app: CreditApplication): List<ExtractedField> {
    val a: ApplicantData = app.applicant
    val p: ProductRequest = app.productRequest
    val baseConf = (a.completeness ?: 0.85).coerceIn(0.0, 1.0)
    return listOf(
        ExtractedField("Producto", p.productLabel ?: p.productType, baseConf, "Detectado del análisis IA"),
        ExtractedField("Monto", p.amount, baseConf, "Producto solicitado"),
        ExtractedField("Plazo", p.term, baseConf - 0.05, "Producto solicitado"),
        ExtractedField("Ingreso mensual", a.estimatedIncome, baseConf - 0.10, "Información del solicitante"),
        ExtractedField("Tipo de empleo", a.employmentType, baseConf - 0.10, "Información del solicitante"),
        ExtractedField("Ubicación", p.location, baseConf - 0.05, "Información extraída"),
        ExtractedField("Teléfono", a.phone, baseConf, "Información de contacto"),
    )
}

private fun sourceFor(app: CreditApplication): String {
    return app.conversationSummary.take(40).ifBlank { "Conversación · ${app.applicant.fullName}" }
}

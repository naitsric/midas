package com.midas.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CallDetail
import com.midas.domain.model.CreditApplication
import com.midas.domain.model.IntentResult
import com.midas.ui.components.ArrowBackGlyph
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CallDetailScreen(
    apiClient: MidasApiClient,
    callId: String,
    onBack: () -> Unit,
    onApplicationGenerated: (CreditApplication) -> Unit = {},
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var call by remember { mutableStateOf<CallDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var intent by remember { mutableStateOf<IntentResult?>(null) }
    var intentLoading by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generatedApp by remember { mutableStateOf<CreditApplication?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(callId) {
        scope.launch {
            try {
                val fetched = apiClient.getCall(callId)
                call = fetched
                if (fetched.status == "completed" && fetched.transcript.isNotBlank()) {
                    intentLoading = true
                    try {
                        intent = apiClient.detectIntentFromCall(callId)
                    } catch (_: Exception) {
                    } finally {
                        intentLoading = false
                    }
                }
            } catch (e: Exception) {
                loadError = e.message ?: s.callDetailLoadError
            } finally {
                loading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }

            call == null || loadError != null -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = loadError ?: s.callDetailLoadError,
                    color = colors.statusNegative,
                )
            }

            else -> CallDetailContent(
                call = call!!,
                intent = intent,
                intentLoading = intentLoading,
                generatedApp = generatedApp,
                generating = generating,
                generationError = generationError,
                onBack = onBack,
                onGenerate = {
                    scope.launch {
                        generating = true
                        generationError = null
                        try {
                            val app = apiClient.generateApplicationFromCall(call!!.id)
                            generatedApp = app
                            onApplicationGenerated(app)
                        } catch (e: Exception) {
                            generationError = e.message ?: "Error"
                        } finally {
                            generating = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CallDetailContent(
    call: CallDetail,
    intent: IntentResult?,
    intentLoading: Boolean,
    generatedApp: CreditApplication?,
    generating: Boolean,
    generationError: String?,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 40.dp),
    ) {
        DetailHeader(idLabel = s.callDetailId, callId = call.id, name = call.clientName, onBack = onBack)
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildMeta(call),
            color = colors.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 48.dp),
        )

        Spacer(Modifier.height(16.dp))
        AudioPlayer(durationSeconds = call.durationSeconds ?: 0)

        // Intent strip
        if (intentLoading) {
            Spacer(Modifier.height(10.dp))
            IntentLoading(label = s.callDetailAnalyzing)
        } else if (intent != null) {
            Spacer(Modifier.height(10.dp))
            IntentStrip(
                intent = intent,
                title = if (intent.intentDetected) s.callDetailIntentTitle else s.callDetailIntentNone,
                confidenceLabel = s.callDetailConfidence,
            )
        }

        // App banner — generated application
        if (generatedApp != null) {
            Spacer(Modifier.height(10.dp))
            AppBanner(app = generatedApp, statusPrefix = s.callDetailAppStatus)
        }

        // Summary
        intent?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(Modifier.height(20.dp))
            SectionHeader(label = s.callDetailSectionSummary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                color = colors.textPrimary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }

        // Extracted data
        val entries = intent?.entities?.filter { (_, v) -> !v.isNullOrBlank() }?.toList().orEmpty()
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionHeader(label = s.callDetailSectionExtracted)
            Spacer(Modifier.height(8.dp))
            ExtractedGrid(items = entries)
        }

        // Generate CTA — only if no app yet and call complete
        if (generatedApp == null && call.status == "completed" && call.transcript.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            GenerateButton(
                label = s.callDetailGenerate,
                loading = generating,
                onClick = onGenerate,
            )
            if (generationError != null) {
                Spacer(Modifier.height(8.dp))
                Text(generationError, color = colors.statusNegative, fontSize = 12.sp)
            }
        }

        // Transcript
        Spacer(Modifier.height(20.dp))
        SectionHeader(label = s.callDetailSectionTranscript)
        Spacer(Modifier.height(10.dp))
        TranscriptBlock(transcript = call.transcript, empty = s.callDetailNoTranscript)
    }
}

// ─────────────────────────── DetailHeader ───────────────────────────

@Composable
private fun DetailHeader(idLabel: String, callId: String, name: String, onBack: () -> Unit) {
    val colors = LocalMidasColors.current
    val buttonBg = if (colors.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(buttonBg)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            ArrowBackGlyph(tint = colors.textPrimary, size = 14.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$idLabel · ${callId.uppercase()}",
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(buttonBg)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ─────────────────────────── AudioPlayer ───────────────────────────

@Composable
private fun AudioPlayer(durationSeconds: Int) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0) }
    val total = durationSeconds.coerceAtLeast(1)

    LaunchedEffect(playing, total) {
        while (playing) {
            delay(1000)
            pos = if (pos >= total) 0 else pos + 1
        }
    }

    val cardBg = if (colors.isDark) accent.copy(alpha = 0.05f) else accent.copy(alpha = 0.06f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable { playing = !playing },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colors.primaryAccentOn,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Waveform(progress = pos.toFloat() / total)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = fmtMMSS(pos),
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = fmtMMSS(total),
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun Waveform(progress: Float) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val bars = 48
        repeat(bars) { i ->
            val raw = abs(sin(i * 0.9f) + cos(i * 0.4f))
            val h = (4f + raw * 10f).dp
            val active = i.toFloat() / bars < progress
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (active) colors.primaryAccent else colors.cardBorder),
            )
        }
    }
}

// ─────────────────────────── Intent ───────────────────────────

@Composable
private fun IntentLoading(label: String) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = colors.primaryAccent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.muted, fontSize = 12.sp)
    }
}

@Composable
private fun IntentStrip(intent: IntentResult, title: String, confidenceLabel: String) {
    val colors = LocalMidasColors.current
    val accent = if (intent.intentDetected) colors.primaryAccent else colors.muted
    val pct = (intent.confidence * 100).toInt()
    val bg = if (colors.isDark) accent.copy(alpha = 0.06f) else accent.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.27f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$confidenceLabel · ",
                    color = colors.muted,
                    fontSize = 10.sp,
                )
                Text(
                    text = "$pct%",
                    color = accent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!intent.productType.isNullOrBlank()) {
                    Text(
                        text = " · ${intent.productType}",
                        color = colors.muted,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── App banner ───────────────────────────

@Composable
private fun AppBanner(app: CreditApplication, statusPrefix: String) {
    val colors = LocalMidasColors.current
    val submitted = app.status.equals("submitted", ignoreCase = true)
    val color = if (submitted) colors.primaryAccent else MidasOrange
    val label = app.statusLabel?.uppercase() ?: app.status.uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.33f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$statusPrefix · $label",
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildAppLabel(app),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        ArrowForwardGlyph(tint = color, size = 14.dp)
    }
}

// ─────────────────────────── Extracted grid ───────────────────────────

@Composable
private fun ExtractedGrid(items: List<Pair<String, String?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { (k, v) ->
                    ExtractedCell(
                        key = k,
                        value = v ?: "",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExtractedCell(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.025f) else Color.Black.copy(alpha = 0.025f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = key.replaceFirstChar { it.uppercase() },
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
    }
}

// ─────────────────────────── Transcript ───────────────────────────

@Composable
private fun TranscriptBlock(transcript: String, empty: String) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)

    if (transcript.isBlank()) {
        Text(
            text = empty,
            color = colors.muted,
            fontSize = 12.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        transcript
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                Text(
                    text = line,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
    }
}

// ─────────────────────────── Section header ───────────────────────────

@Composable
private fun SectionHeader(label: String) {
    val colors = LocalMidasColors.current
    Text(
        text = label,
        color = colors.muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
        fontFamily = FontFamily.Monospace,
    )
}

// ─────────────────────────── Generate CTA ───────────────────────────

@Composable
private fun GenerateButton(label: String, loading: Boolean, onClick: () -> Unit) {
    val colors = LocalMidasColors.current
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryAccent,
            contentColor = colors.primaryAccentOn,
            disabledContainerColor = colors.primaryAccent.copy(alpha = 0.4f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.primaryAccentOn,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

// ─────────────────────────── helpers ───────────────────────────

private fun fmtMMSS(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun buildMeta(call: CallDetail): String {
    val when_ = call.createdAt.takeIf { it.isNotBlank() }
    val dur = call.durationSeconds?.let { "${it / 60}m ${it % 60}s" }
    return listOfNotNull(when_, dur).joinToString(" · ")
}

private fun buildAppLabel(app: CreditApplication): String {
    val product = app.productRequest.productLabel ?: app.productRequest.productType
    val amount = app.productRequest.amount
    return listOfNotNull(product, amount).joinToString(" · ").ifBlank { app.applicant.fullName }
}

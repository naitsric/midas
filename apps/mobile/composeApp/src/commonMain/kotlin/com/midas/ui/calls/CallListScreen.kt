package com.midas.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CallSummary
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasOrange
import com.midas.ui.theme.MidasPurple
import kotlinx.coroutines.launch

private enum class CallsTab { All, Intent, NoIntent }

@Composable
fun CallListScreen(
    apiClient: MidasApiClient,
    onNewRecording: () -> Unit,
    onCallClick: (String) -> Unit = {},
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    var calls by remember { mutableStateOf<List<CallSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(CallsTab.All) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                calls = apiClient.listCalls()
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 80.dp),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = s.callsTitle,
                    color = colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = "${calls.size} ${s.callsTotalSuffix}",
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(14.dp))

            CallsTabsRow(
                tab = tab,
                onTabChange = { tab = it },
                allLabel = s.callsTabAll,
                intentLabel = s.callsTabIntent,
                noIntentLabel = s.callsTabNoIntent,
            )

            Spacer(Modifier.height(18.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    }
                }
                calls.isEmpty() -> {
                    EmptyState(
                        title = s.callsEmptyTitle,
                        body = s.callsEmptyBody,
                        cta = s.callsEmptyCta,
                        footer = s.callsEmptyFooter,
                        onRecord = onNewRecording,
                    )
                }
                else -> {
                    val grouped = remember(calls) { groupByDay(calls) }
                    grouped.forEach { (day, items) ->
                        DayHeader(label = day)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items.forEach { call ->
                                CallRow(
                                    call = call,
                                    onClick = { onCallClick(call.id) },
                                    statusReady = s.callsStatusReady,
                                    statusProcessing = s.callsStatusProcessing,
                                    statusError = s.callsStatusError,
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

// ──────────────────── Tabs ────────────────────

@Composable
private fun CallsTabsRow(
    tab: CallsTab,
    onTabChange: (CallsTab) -> Unit,
    allLabel: String,
    intentLabel: String,
    noIntentLabel: String,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TabPill(
            label = allLabel,
            selected = tab == CallsTab.All,
            onClick = { onTabChange(CallsTab.All) },
            modifier = Modifier.weight(1f),
        )
        TabPill(
            label = intentLabel,
            selected = tab == CallsTab.Intent,
            onClick = { onTabChange(CallsTab.Intent) },
            modifier = Modifier.weight(1f),
        )
        TabPill(
            label = noIntentLabel,
            selected = tab == CallsTab.NoIntent,
            onClick = { onTabChange(CallsTab.NoIntent) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) colors.primaryAccent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.primaryAccentOn else colors.muted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ──────────────────── Day header & rows ────────────────────

@Composable
private fun DayHeader(label: String) {
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

@Composable
private fun CallRow(
    call: CallSummary,
    onClick: () -> Unit,
    statusReady: String,
    statusProcessing: String,
    statusError: String,
) {
    val colors = LocalMidasColors.current
    val statusColor = when (call.status.lowercase()) {
        "completed", "ready" -> colors.primaryAccent
        "recording", "processing" -> MidasOrange
        "error", "failed" -> colors.statusNegative
        else -> MidasPurple
    }
    val statusLabel = when (call.status.lowercase()) {
        "recording", "processing" -> statusProcessing
        "error", "failed" -> statusError
        else -> statusReady
    }
    val iconAccent = MidasPurple
    val rowBg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconAccent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = iconAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.clientName,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = formatTimeAndDuration(call),
                    color = colors.muted,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ──────────────────── Empty state ────────────────────

@Composable
private fun EmptyState(
    title: String,
    body: String,
    cta: String,
    footer: String,
    onRecord: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.08f),
                    1f to accent.copy(alpha = 0.02f),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = 0.20f),
                        0.7f to Color.Transparent,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            color = colors.muted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 280.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRecord,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = colors.primaryAccentOn,
            ),
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = colors.primaryAccentOn,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = cta,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = footer,
            color = colors.muted,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
        )
    }
}

// ──────────────────── Day grouping ────────────────────

private fun groupByDay(calls: List<CallSummary>): List<Pair<String, List<CallSummary>>> {
    val groups = linkedMapOf<String, MutableList<CallSummary>>()
    for (call in calls) {
        val key = dayKey(call.createdAt)
        groups.getOrPut(key) { mutableListOf() }.add(call)
    }
    return groups.entries.map { it.key to it.value }
}

/** Extract date portion of an ISO-8601 timestamp like "2026-04-20T15:32:00Z". */
private fun dayKey(createdAt: String): String {
    val date = createdAt.substringBefore('T').takeIf { it.isNotEmpty() } ?: return "—"
    return date.uppercase()
}

private fun formatTimeAndDuration(call: CallSummary): String {
    val time = call.createdAt.substringAfter('T', "").take(5)
    val duration = call.durationSeconds?.let { "${it / 60}m ${it % 60}s" }
    return when {
        time.isNotEmpty() && duration != null -> "$time · $duration"
        time.isNotEmpty() -> time
        duration != null -> duration
        else -> "—"
    }
}

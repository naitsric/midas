package com.midas.ui.dashboard

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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.Advisor
import com.midas.domain.model.CallSummary
import com.midas.domain.model.CreditApplication
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.components.MidasMonogram
import com.midas.ui.i18n.Language
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasBlue
import com.midas.ui.theme.MidasOrange
import com.midas.ui.theme.MidasPurple
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    apiClient: MidasApiClient,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    onLogout: () -> Unit,
    onRecord: () -> Unit = {},
    onCall: () -> Unit = {},
    onProfile: () -> Unit = {},
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var advisor by remember { mutableStateOf<Advisor?>(null) }
    var conversationCount by remember { mutableStateOf(0) }
    var applicationCount by remember { mutableStateOf(0) }
    var callCount by remember { mutableStateOf(0) }
    var pendingCount by remember { mutableStateOf(0) }
    var recentApps by remember { mutableStateOf<List<CreditApplication>>(emptyList()) }
    var recentCalls by remember { mutableStateOf<List<CallSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                advisor = apiClient.getAdvisorProfile()
                val convos = apiClient.listConversations()
                val apps = apiClient.listApplications()
                val calls = apiClient.listCalls()
                conversationCount = convos.size
                applicationCount = apps.size
                callCount = calls.size
                pendingCount = apps.count { it.status.equals("draft", ignoreCase = true) }
                recentApps = apps.take(3)
                recentCalls = calls.take(3)
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
                .padding(top = 16.dp, bottom = 80.dp),
        ) {
            DashTopBar(
                advisorInitial = advisor?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                onlineLabel = s.dashboardOnline,
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange,
                onLogout = onLogout,
                onProfile = onProfile,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = s.dashboardGreeting,
                color = colors.muted,
                fontSize = 13.sp,
            )
            Text(
                text = advisor?.name ?: "…",
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(14.dp))

            MiniTicker()

            Spacer(Modifier.height(18.dp))

            HeroActionCard(
                chipLabel = s.dashboardHeroChip,
                title = s.dashboardHeroTitle,
                body = s.dashboardHeroSubtitle,
                recordLabel = s.dashboardHeroRecord,
                callLabel = s.dashboardHeroCall,
                onRecord = onRecord,
                onCall = onCall,
            )

            Spacer(Modifier.height(18.dp))

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryAccent)
                }
            } else {
                StatGrid(
                    conversations = conversationCount,
                    conversationsLabel = s.dashboardStatConversations,
                    calls = callCount,
                    callsLabel = s.dashboardStatCalls,
                    applications = applicationCount,
                    applicationsLabel = s.dashboardStatApplications,
                    pending = pendingCount,
                    pendingLabel = s.dashboardStatPending,
                    newBadge = s.dashboardStatNew,
                )

                Spacer(Modifier.height(24.dp))

                SectionHeader(
                    label = s.dashboardRecentSection,
                    right = s.dashboardRecentSeeAll,
                )

                Spacer(Modifier.height(10.dp))

                if (recentCalls.isEmpty() && recentApps.isEmpty()) {
                    Text(
                        text = s.dashboardEmpty,
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentCalls.forEach { call ->
                            ActivityRow(
                                icon = Icons.Default.Mic,
                                accent = when (call.status.lowercase()) {
                                    "completed" -> colors.primaryAccent
                                    "recording" -> MidasOrange
                                    else -> MidasPurple
                                },
                                title = call.clientName,
                                subtitle = call.durationSeconds?.let { "${it / 60}m ${it % 60}s" } ?: "",
                                status = call.status.uppercase(),
                                time = "",
                            )
                        }
                        recentApps.forEach { app ->
                            ActivityRow(
                                icon = Icons.Default.Description,
                                accent = when (app.status.lowercase()) {
                                    "draft" -> MidasOrange
                                    "submitted" -> colors.primaryAccent
                                    else -> MidasBlue
                                },
                                title = app.applicant.fullName,
                                subtitle = app.productRequest.productLabel
                                    ?: app.productRequest.productType ?: "",
                                status = (app.statusLabel ?: app.status).uppercase(),
                                time = "",
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────── Top bar ────────────────────

@Composable
private fun DashTopBar(
    advisorInitial: String,
    onlineLabel: String,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    onLogout: () -> Unit,
    onProfile: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MidasMonogram(size = 40.dp, cornerRadius = 10.dp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Online pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.statusPositive),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = onlineLabel,
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                )
            }

            TextButton(
                onClick = {
                    onLanguageChange(if (currentLanguage == Language.ES) Language.EN else Language.ES)
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    text = if (currentLanguage == Language.ES) "EN" else "ES",
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            IconButton(onClick = onLogout, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Avatar (tap → profile)
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent)
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = advisorInitial,
                    color = colors.primaryAccentOn,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ──────────────────── Mini ticker ────────────────────

@Composable
private fun MiniTicker() {
    val colors = LocalMidasColors.current
    val scroll = rememberScrollState()
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)

    LaunchedEffect(Unit) {
        while (true) {
            val target = scroll.maxValue
            if (target <= 0) {
                kotlinx.coroutines.delay(120)
                continue
            }
            scroll.animateScrollTo(
                value = target,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = (target * 30).coerceAtLeast(10000),
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
            )
            scroll.scrollTo(0)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LIVE",
            color = colors.statusPositive,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll, enabled = false),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val items = listOf(
                    Triple("BMV", "56,482", "+0.84%" to true),
                    Triple("USD/MXN", "17.23", "−0.12%" to false),
                    Triple("CETES", "10.28%", "+0.02%" to true),
                    Triple("TIIE", "9.84%", "−0.05%" to false),
                )
                (items + items).forEach { (sym, value, delta) ->
                    Text(
                        text = sym,
                        color = colors.muted.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = value,
                        color = colors.muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = delta.first,
                        color = if (delta.second) colors.statusPositive else colors.statusNegative,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(22.dp))
                }
            }
        }
    }
}

// ──────────────────── Hero action card ────────────────────

@Composable
private fun HeroActionCard(
    chipLabel: String,
    title: String,
    body: String,
    recordLabel: String,
    callLabel: String,
    onRecord: () -> Unit,
    onCall: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.08f),
                    1f to accent.copy(alpha = 0.03f),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            text = chipLabel,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            color = colors.muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroButton(
                label = recordLabel,
                icon = Icons.Default.Mic,
                primary = true,
                onClick = onRecord,
                modifier = Modifier.weight(1f),
            )
            HeroButton(
                label = callLabel,
                icon = Icons.Default.Phone,
                primary = false,
                onClick = onCall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val bg = if (primary) colors.primaryAccent else Color.Transparent
    val contentColor = if (primary) colors.primaryAccentOn else colors.textPrimary
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ──────────────────── Stat grid ────────────────────

@Composable
private fun StatGrid(
    conversations: Int,
    conversationsLabel: String,
    calls: Int,
    callsLabel: String,
    applications: Int,
    applicationsLabel: String,
    pending: Int,
    pendingLabel: String,
    newBadge: String,
) {
    val colors = LocalMidasColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMini(
                label = conversationsLabel,
                value = conversations,
                accent = colors.primaryAccent,
                modifier = Modifier.weight(1f),
            )
            StatMini(
                label = callsLabel,
                value = calls,
                accent = MidasPurple,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatMini(
                label = applicationsLabel,
                value = applications,
                accent = MidasOrange,
                modifier = Modifier.weight(1f),
            )
            StatMini(
                label = pendingLabel,
                value = pending,
                accent = MidasOrange,
                badge = if (pending > 0) newBadge else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatMini(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value.toString(),
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = badge,
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

// ──────────────────── Section header + activity row ────────────────────

@Composable
private fun SectionHeader(label: String, right: String?) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
        if (right != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = right,
                    color = colors.primaryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(4.dp))
                ArrowForwardGlyph(tint = colors.primaryAccent, size = 12.dp)
            }
        }
    }
}

@Composable
private fun ActivityRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    status: String,
    time: String,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = colors.muted,
                    fontSize = 11.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = status,
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.0.sp,
            )
            if (time.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = time,
                    color = colors.muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

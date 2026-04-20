package com.midas.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.Advisor
import com.midas.ui.components.ArrowBackGlyph
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.Language
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasOrange
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    apiClient: MidasApiClient,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    apiKey: String?,
    voipConnected: Boolean,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var advisor by remember { mutableStateOf<Advisor?>(null) }
    var conversationsCount by remember { mutableStateOf(0) }
    var applicationsCount by remember { mutableStateOf(0) }
    var submittedCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            runCatching { apiClient.getAdvisorProfile() }.getOrNull()?.let { advisor = it }
            runCatching { apiClient.listConversations() }.getOrNull()?.let { conversationsCount = it.size }
            runCatching { apiClient.listApplications() }.getOrNull()?.let { list ->
                applicationsCount = list.size
                submittedCount = list.count { it.status.equals("submitted", ignoreCase = true) }
            }
        }
    }

    val conversion = if (conversationsCount > 0) {
        ((submittedCount.toDouble() / conversationsCount.toDouble()) * 100).toInt()
    } else 0

    // Profile-local state (no backend yet)
    var autoApp by remember { mutableStateOf(true) }
    var themePref by remember { mutableStateOf("auto") }
    var notifHot by remember { mutableStateOf(true) }
    var notifNewApp by remember { mutableStateOf(true) }
    var notifWeekly by remember { mutableStateOf(false) }
    var notifPush by remember { mutableStateOf(true) }
    var consent by remember { mutableStateOf(true) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            ProfileHeader(
                overline = s.profileHeaderOverline,
                title = s.profileTitle,
                saveLabel = s.profileSave,
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp, bottom = 30.dp),
            ) {
                IdentityHero(
                    name = advisor?.name ?: "…",
                    email = advisor?.email,
                    advisorId = (advisor?.id ?: "").take(6).uppercase(),
                    advisorPrefix = s.profileAdvisorId,
                    activeLabel = s.profileActiveLabel,
                    status = advisor?.status ?: "",
                )

                Spacer(Modifier.height(20.dp))

                PerformanceCard(
                    chipLabel = s.profilePerfChip,
                    conversationsLabel = s.profileStatConversations,
                    conversations = conversationsCount,
                    applicationsLabel = s.profileStatApplications,
                    applications = applicationsCount,
                    conversionLabel = s.profileStatConversion,
                    conversion = conversion,
                )

                // ─── 01 Contacto ───
                Section(num = "01", title = s.profileSectionContact) {
                    EditableRow(
                        icon = Icons.Default.Email,
                        label = s.profileContactEmail,
                        value = advisor?.email ?: "—",
                    )
                    Divider()
                    EditableRow(
                        icon = Icons.Default.Phone,
                        label = s.profileContactPhone,
                        value = advisor?.phone ?: "—",
                    )
                    Divider()
                    EditableRow(
                        icon = Icons.Default.LocationOn,
                        label = s.profileContactRegion,
                        value = "—",
                    )
                }

                // ─── 02 Preferencias ───
                Section(num = "02", title = s.profileSectionPreferences) {
                    ToggleRow(
                        label = s.profilePrefAutoAppLabel,
                        desc = s.profilePrefAutoAppDesc,
                        checked = autoApp,
                        onToggle = { autoApp = !autoApp },
                    )
                    Divider()
                    SelectRow(
                        label = s.profilePrefLanguage,
                        value = currentLanguage.code,
                        options = listOf(
                            "es" to s.profileLangEs,
                            "en" to s.profileLangEn,
                        ),
                        onSelect = { code ->
                            val lang = if (code == "en") Language.EN else Language.ES
                            onLanguageChange(lang)
                        },
                    )
                    Divider()
                    SelectRow(
                        label = s.profilePrefTheme,
                        value = themePref,
                        options = listOf(
                            "dark" to s.profileThemeDark,
                            "light" to s.profileThemeLight,
                            "auto" to s.profileThemeAuto,
                        ),
                        onSelect = { themePref = it },
                    )
                }

                // ─── 03 Notificaciones ───
                Section(num = "03", title = s.profileSectionNotifications) {
                    ToggleRow(
                        label = s.profileNotifHotLabel,
                        desc = s.profileNotifHotDesc,
                        checked = notifHot,
                        onToggle = { notifHot = !notifHot },
                    )
                    Divider()
                    ToggleRow(
                        label = s.profileNotifNewAppLabel,
                        desc = s.profileNotifNewAppDesc,
                        checked = notifNewApp,
                        onToggle = { notifNewApp = !notifNewApp },
                    )
                    Divider()
                    ToggleRow(
                        label = s.profileNotifWeeklyLabel,
                        desc = s.profileNotifWeeklyDesc,
                        checked = notifWeekly,
                        onToggle = { notifWeekly = !notifWeekly },
                    )
                    Divider()
                    ToggleRow(
                        label = s.profileNotifPushLabel,
                        desc = s.profileNotifPushDesc,
                        checked = notifPush,
                        onToggle = { notifPush = !notifPush },
                    )
                }

                // ─── 04 Integraciones ───
                Section(num = "04", title = s.profileSectionIntegrations) {
                    IntegrationRow(
                        name = s.profileIntegrationWhatsApp,
                        meta = s.profileIntegrationWhatsAppMeta,
                        connected = false,
                        connectLabel = s.profileIntegrationConnect,
                        manageLabel = s.profileIntegrationManage,
                    )
                    Divider()
                    IntegrationRow(
                        name = s.profileIntegrationVoIP,
                        meta = s.profileIntegrationVoIPMeta,
                        connected = voipConnected,
                        connectLabel = s.profileIntegrationConnect,
                        manageLabel = s.profileIntegrationManage,
                    )
                    Divider()
                    IntegrationRow(
                        name = s.profileIntegrationCalendar,
                        meta = s.profileIntegrationCalendarMeta,
                        connected = false,
                        connectLabel = s.profileIntegrationConnect,
                        manageLabel = s.profileIntegrationManage,
                    )
                }

                // ─── 05 Privacidad ───
                Section(num = "05", title = s.profileSectionPrivacy) {
                    ToggleRow(
                        label = s.profilePrivacyConsentLabel,
                        desc = s.profilePrivacyConsentDesc,
                        checked = consent,
                        onToggle = { consent = !consent },
                    )
                    Divider()
                    LinkRow(
                        label = s.profilePrivacyDownload,
                        meta = s.profilePrivacyDownloadMeta,
                    )
                    Divider()
                    LinkRow(label = s.profilePrivacyPolicy)
                    Divider()
                    LinkRow(label = s.profilePrivacyTerms)
                }

                // ─── 06 Sesión ───
                Section(num = "06", title = s.profileSectionSession) {
                    ApiKeyRow(
                        label = s.profileApiKeyLabel,
                        keyValue = apiKey,
                        revealed = apiKeyVisible,
                        onToggleReveal = { apiKeyVisible = !apiKeyVisible },
                        revealLabel = s.profileApiKeyReveal,
                        rotatedLabel = s.profileApiKeyRotateDays,
                    )
                    Divider()
                    LinkRow(label = s.profileSessionRotate, danger = true)
                    Divider()
                    LinkRow(
                        label = s.profileSessionLogout,
                        danger = true,
                        onClick = onLogout,
                    )
                }

                Spacer(Modifier.height(24.dp))

                val advisorIdFooter = (advisor?.id ?: "").take(6).uppercase().ifBlank { "——" }
                Text(
                    text = "${s.profileFooterTemplate} · ${s.profileAdvisorId}-$advisorIdFooter",
                    color = colors.muted.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────── Header ───────────────────────────

@Composable
private fun ProfileHeader(
    overline: String,
    title: String,
    saveLabel: String,
    onBack: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    text = overline.uppercase(),
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp,
                )
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryAccent)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = saveLabel,
                    color = colors.primaryAccentOn,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.cardBorder),
        )
    }
}

// ─────────────────────────── Identity hero ───────────────────────────

@Composable
private fun IdentityHero(
    name: String,
    email: String?,
    advisorId: String,
    advisorPrefix: String,
    activeLabel: String,
    status: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Row(verticalAlignment = Alignment.Top) {
        // Avatar 68dp rounded square
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        0f to accent,
                        1f to accent.copy(alpha = 0.7f),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(name),
                color = Color.Black,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            // "+" edit badge bottom-right
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xFF1E1E1E) else Color.White)
                    .border(1.5.dp, colors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                softWrap = false,
            )
            if (!email.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = email,
                    color = accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.statusPositive),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$advisorPrefix-$advisorId · ${activeLabel.takeIf { status.equals("active", ignoreCase = true) } ?: status.uppercase()}",
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.0.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ─────────────────────────── Performance card ───────────────────────────

@Composable
private fun PerformanceCard(
    chipLabel: String,
    conversationsLabel: String,
    conversations: Int,
    applicationsLabel: String,
    applications: Int,
    conversionLabel: String,
    conversion: Int,
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
            .padding(14.dp),
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
                text = chipLabel,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PerfMetric(label = conversationsLabel, value = conversations.toString(), modifier = Modifier.weight(1f))
            PerfMetric(label = applicationsLabel, value = applications.toString(), modifier = Modifier.weight(1f))
            PerfMetric(label = conversionLabel, value = "$conversion%", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PerfMetric(label: String, value: String, modifier: Modifier = Modifier) {
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ─────────────────────────── Section shell ───────────────────────────

@Composable
private fun Section(num: String, title: String, content: @Composable () -> Unit) {
    val colors = LocalMidasColors.current
    Column(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$num /",
                color = colors.primaryAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
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
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.cardBorder),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.02f)
                    else Color.White,
                )
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun Divider() {
    val colors = LocalMidasColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.cardBorder),
    )
}

// ─────────────────────────── Rows ───────────────────────────

@Composable
private fun EditableRow(icon: ImageVector, label: String, value: String) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.04f)
                    else Color.Black.copy(alpha = 0.04f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        ArrowForwardGlyph(tint = colors.muted.copy(alpha = 0.6f), size = 10.dp)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    desc: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = colors.muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        PillSwitch(checked = checked, onToggle = onToggle)
    }
}

@Composable
private fun PillSwitch(checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalMidasColors.current
    val trackColor = if (checked) colors.primaryAccent else {
        if (colors.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.12f)
    }
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .clickable(onClick = onToggle),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Color.Black else Color.White),
        )
    }
}

@Composable
private fun SelectRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    val colors = LocalMidasColors.current
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (code, displayName) ->
                val selected = code == value
                val contentColor = if (selected) colors.primaryAccent else colors.textPrimary
                val borderColor = if (selected) colors.primaryAccent.copy(alpha = 0.45f) else colors.cardBorder
                val bg = if (selected) colors.primaryAccent.copy(alpha = 0.15f) else Color.Transparent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable { onSelect(code) }
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName,
                        color = contentColor,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(
    label: String,
    meta: String? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalMidasColors.current
    val labelColor = if (danger) colors.statusNegative else colors.textPrimary
    val arrowColor = if (danger) colors.statusNegative.copy(alpha = 0.6f) else colors.muted.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = meta,
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        ArrowForwardGlyph(tint = arrowColor, size = 10.dp)
    }
}

@Composable
private fun IntegrationRow(
    name: String,
    meta: String,
    connected: Boolean,
    connectLabel: String,
    manageLabel: String,
) {
    val colors = LocalMidasColors.current
    val color = if (connected) colors.primaryAccent else colors.muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.LinkOff,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                color = colors.muted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (connected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = manageLabel,
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.primaryAccent)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = connectLabel,
                    color = colors.primaryAccentOn,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}

@Composable
private fun ApiKeyRow(
    label: String,
    keyValue: String?,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    revealLabel: String,
    rotatedLabel: String,
) {
    val colors = LocalMidasColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = colors.muted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayKey(keyValue, revealed),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(5.dp))
                    .clickable(onClick = onToggleReveal)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = revealLabel,
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
        }
        if (!keyValue.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$rotatedLabel —",
                color = colors.muted,
                fontSize = 10.5.sp,
            )
        }
    }
}

// ─────────────────────────── helpers ───────────────────────────

private fun displayKey(key: String?, revealed: Boolean): String {
    if (key.isNullOrBlank()) return "——"
    return if (revealed) {
        key
    } else {
        val suffix = key.takeLast(4)
        "mk_live_••••••••••••$suffix"
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

@Suppress("unused")
private val _orangeAnchor = MidasOrange

package com.midas.ui.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.ui.components.ArrowBackGlyph
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.components.MidasMonogram
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors

private enum class Phase { Welcome, Permissions, Aha }

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    var phase by remember { mutableStateOf(Phase.Welcome) }
    var micGranted by remember { mutableStateOf(false) }
    var notifsGranted by remember { mutableStateOf(false) }

    val phaseIndex = when (phase) {
        Phase.Welcome -> 0
        Phase.Permissions -> 1
        Phase.Aha -> 2
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        // ─── Dimmed stage behind the sheet ───
        MidasBackdrop()
        StageBehind(
            statusText = s.onbStageStatus.formatStep(if (phase == Phase.Aha) 4 else phaseIndex + 1, 3),
            greeting = s.onbStageGreeting,
            subtitle = s.onbStageSubtitle,
        )
        // Darkening scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (colors.isDark) Color.Black.copy(alpha = 0.40f)
                    else Color.Black.copy(alpha = 0.08f),
                ),
        )

        // ─── Bottom sheet ───
        BottomSheet(modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
                Spacer(Modifier.height(8.dp))
                SheetHeader(
                    stepLabel = s.onbStepOf.formatStep(phaseIndex + 1, 3),
                    step = phaseIndex,
                    total = 3,
                )
                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.animateContentSize()) {
                    when (phase) {
                        Phase.Welcome -> WelcomePhase(
                            titlePrefix = s.onbWelcomeTitlePrefix,
                            body = s.onbWelcomeBody,
                            journeyPermissions = s.onbJourneyPermissions,
                            journeyFirstCall = s.onbJourneyFirstCall,
                            journeyApplication = s.onbJourneyApplication,
                        )
                        Phase.Permissions -> PermissionsPhase(
                            title = s.onbPermsTitle,
                            body = s.onbPermsBody,
                            micName = s.onbPermMic,
                            micSub = s.onbPermMicSub,
                            notifsName = s.onbPermNotifs,
                            notifsSub = s.onbPermNotifsSub,
                            allowLabel = s.onbPermAllow,
                            grantedLabel = s.onbPermGranted,
                            assurance = s.onbPermAssurance,
                            micGranted = micGranted,
                            notifsGranted = notifsGranted,
                            onGrantMic = { micGranted = true },
                            onGrantNotifs = { notifsGranted = true },
                        )
                        Phase.Aha -> AhaPhase(
                            chip = s.onbAhaChip,
                            title = s.onbAhaTitle,
                            body = s.onbAhaBody,
                            callLabel = s.onbAhaCallLabel,
                            callName = s.onbAhaCallName,
                            callQuote = s.onbAhaCallQuote,
                            appLabel = s.onbAhaAppLabel,
                            appProduct = s.onbAhaAppProduct,
                            appCompleteness = s.onbAhaAppCompleteness,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SheetButtons(
                    showBack = phase != Phase.Welcome,
                    nextLabel = if (phase == Phase.Aha) s.onbRecordFirstCall else s.onbContinue,
                    onBack = {
                        phase = when (phase) {
                            Phase.Permissions -> Phase.Welcome
                            Phase.Aha -> Phase.Permissions
                            Phase.Welcome -> Phase.Welcome
                        }
                    },
                    onNext = {
                        phase = when (phase) {
                            Phase.Welcome -> Phase.Permissions
                            Phase.Permissions -> Phase.Aha
                            Phase.Aha -> {
                                onDone()
                                Phase.Aha
                            }
                        }
                    },
                )

                if (phase != Phase.Aha) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = s.onbSkipToDashboard,
                        color = colors.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDone() }
                            .padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ──────────────────── Phase content ────────────────────

@Composable
private fun WelcomePhase(
    titlePrefix: String,
    body: String,
    journeyPermissions: String,
    journeyFirstCall: String,
    journeyApplication: String,
) {
    val colors = LocalMidasColors.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MidasMonogram(size = 48.dp, cornerRadius = 12.dp)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = buildAnnotatedString {
                append(titlePrefix)
                append(" ")
                withStyle(SpanStyle(color = colors.primaryAccent)) {
                    append("Midas")
                }
            },
            color = colors.textPrimary,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = body,
            color = colors.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(18.dp))

        JourneyStrip(
            permissions = journeyPermissions,
            firstCall = journeyFirstCall,
            application = journeyApplication,
        )
    }
}

@Composable
private fun JourneyStrip(permissions: String, firstCall: String, application: String) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .horizontalScroll(scroll)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JourneyStep(num = "01", label = permissions, accent = true)
        JourneyDot()
        JourneyStep(num = "02", label = firstCall, accent = true)
        JourneyDot()
        JourneyStep(num = "03", label = application, accent = false)
    }
}

@Composable
private fun JourneyStep(num: String, label: String, accent: Boolean) {
    val colors = LocalMidasColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$num →",
            color = if (accent) colors.primaryAccent else colors.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = colors.muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun JourneyDot() {
    val colors = LocalMidasColors.current
    Spacer(Modifier.width(8.dp))
    Text(
        text = "·",
        color = colors.muted.copy(alpha = 0.5f),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        softWrap = false,
    )
    Spacer(Modifier.width(8.dp))
}

@Composable
private fun PermissionsPhase(
    title: String,
    body: String,
    micName: String,
    micSub: String,
    notifsName: String,
    notifsSub: String,
    allowLabel: String,
    grantedLabel: String,
    assurance: String,
    micGranted: Boolean,
    notifsGranted: Boolean,
    onGrantMic: () -> Unit,
    onGrantNotifs: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Column {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            color = colors.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(18.dp))

        PermissionCard(
            icon = Icons.Default.Mic,
            name = micName,
            subtitle = micSub,
            allowLabel = allowLabel,
            grantedLabel = grantedLabel,
            granted = micGranted,
            onGrant = onGrantMic,
        )
        Spacer(Modifier.height(10.dp))
        PermissionCard(
            icon = Icons.Default.Notifications,
            name = notifsName,
            subtitle = notifsSub,
            allowLabel = allowLabel,
            grantedLabel = grantedLabel,
            granted = notifsGranted,
            onGrant = onGrantNotifs,
        )

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = assurance,
                color = colors.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    subtitle: String,
    allowLabel: String,
    grantedLabel: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val cardBg = if (colors.isDark) accent.copy(alpha = 0.05f) else accent.copy(alpha = 0.06f)
    val borderColor = if (granted) accent else colors.cardBorder
    val iconBg = if (colors.isDark) accent.copy(alpha = 0.10f) else accent.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = grantedLabel,
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.06f),
                    )
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = allowLabel,
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AhaPhase(
    chip: String,
    title: String,
    body: String,
    callLabel: String,
    callName: String,
    callQuote: String,
    appLabel: String,
    appProduct: String,
    appCompleteness: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Column {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (colors.isDark) accent.copy(alpha = 0.10f)
                    else accent.copy(alpha = 0.10f),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = chip,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            color = colors.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(4.dp))

        AhaIllustration(
            callLabel = callLabel,
            callName = callName,
            callQuote = callQuote,
            appLabel = appLabel,
            appProduct = appProduct,
            appCompleteness = appCompleteness,
        )
    }
}

@Composable
private fun AhaIllustration(
    callLabel: String,
    callName: String,
    callQuote: String,
    appLabel: String,
    appProduct: String,
    appCompleteness: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val transition = rememberInfiniteTransition(label = "aha-dot")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .padding(vertical = 4.dp),
    ) {
        // LEFT: call card — tilted -3°
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 10.dp, y = (-14).dp)
                .rotate(-3f)
                .width(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (colors.isDark) Color.White.copy(alpha = 0.03f)
                    else Color.White,
                )
                .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350).copy(alpha = pulse)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = callLabel,
                    color = Color(0xFFEF5350),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = callName,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = callQuote,
                color = colors.muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }

        // CENTER: arrow
        Box(
            modifier = Modifier.align(Alignment.Center),
        ) {
            ArrowForwardGlyph(tint = accent, size = 36.dp)
        }

        // RIGHT: application card — tilted +2°
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-10).dp, y = 10.dp)
                .rotate(2f)
                .width(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (colors.isDark) accent.copy(alpha = 0.06f)
                    else accent.copy(alpha = 0.08f),
                )
                .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(
                text = appLabel,
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = callName,
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = appProduct,
                color = colors.muted,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = appCompleteness,
                color = colors.muted,
                fontSize = 10.sp,
            )
        }
    }
}

// ──────────────────── Sheet chrome ────────────────────

@Composable
private fun BoxScope.BottomSheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalMidasColors.current
    val sheetBg = if (colors.isDark) Color(0xFF141414) else Color(0xFFFFFFFF)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(sheetBg)
            .border(
                width = 1.dp,
                color = colors.cardBorder,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.18f)
                        else Color.Black.copy(alpha = 0.15f),
                    ),
            )
        }
        content()
    }
}

@Composable
private fun SheetHeader(stepLabel: String, step: Int, total: Int) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stepLabel.uppercase(),
            color = colors.primaryAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            fontFamily = FontFamily.Monospace,
        )
        ProgressDots(step = step, total = total)
    }
}

@Composable
private fun ProgressDots(step: Int, total: Int) {
    val colors = LocalMidasColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val width by animateDpAsState(
                targetValue = if (i == step) 22.dp else 4.dp,
                animationSpec = tween(240),
                label = "dotWidth",
            )
            val color = if (i <= step) colors.primaryAccent else colors.cardBorder
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(width)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun SheetButtons(
    showBack: Boolean,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                ArrowBackGlyph(tint = colors.textPrimary)
            }
        }
        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primaryAccent,
                contentColor = colors.primaryAccentOn,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = nextLabel,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.width(8.dp))
            ArrowForwardGlyph(tint = colors.primaryAccentOn)
        }
    }
}

// ──────────────────── Stage preview behind sheet ────────────────────

@Composable
private fun StageBehind(statusText: String, greeting: String, subtitle: String) {
    val colors = LocalMidasColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 70.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.statusPositive),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = statusText,
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp,
            )
        }
        Spacer(Modifier.height(40.dp))
        MidasMonogram()
        Spacer(Modifier.height(16.dp))
        Text(
            text = greeting,
            color = colors.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = colors.muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        Spacer(Modifier.weight(1f))
        // Skeleton cards peeking behind the sheet at opacity 0.35
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (colors.isDark) Color.White.copy(alpha = 0.03f * 0.35f)
                            else Color.Black.copy(alpha = 0.03f * 0.35f),
                        )
                        .border(
                            1.dp,
                            colors.cardBorder.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp),
                        ),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ──────────────────── util ────────────────────

private fun String.formatStep(current: Int, total: Int): String =
    this
        .replace("%1\$d", current.toString())
        .replace("%2\$d", total.toString())

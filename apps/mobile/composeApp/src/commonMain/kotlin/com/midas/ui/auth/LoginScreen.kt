package com.midas.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.data.repository.SettingsRepository
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.components.MidasMonogram
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    apiClient: MidasApiClient,
    settings: SettingsRepository,
    onLoginSuccess: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var pasteHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val txt = clipboard.getText()?.text?.trim().orEmpty()
        if (txt.startsWith("mk_")) pasteHint = txt
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        MidasBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ───── Top group ─────
            Column(horizontalAlignment = Alignment.Start) {
                StatusPill(label = s.loginStatusOnline)

                Spacer(Modifier.height(48.dp))

                MidasMonogram()

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Midas",
                    color = colors.textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.6).sp,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = buildAnnotatedString {
                        append(s.loginSubtitlePrefix)
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(s.loginSubtitleAccent)
                        }
                    },
                    color = colors.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.widthIn(max = 280.dp),
                )

                Spacer(Modifier.height(28.dp))

                MarketTicker()
            }

            // ───── Bottom group ─────
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = s.loginTitle,
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))

                ApiKeyField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        error = null
                        if (it.isNotEmpty()) pasteHint = null
                    },
                    hasError = error != null,
                )

                if (apiKey.isEmpty() && pasteHint != null) {
                    Spacer(Modifier.height(8.dp))
                    ClipboardSuggestion(
                        title = s.loginPasteTitle,
                        preview = maskClipboard(pasteHint!!),
                        action = s.loginPasteAction,
                        onClick = {
                            apiKey = pasteHint!!
                            pasteHint = null
                            error = null
                        },
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    ErrorRow(text = error!!, color = colors.statusNegative)
                }

                Spacer(Modifier.height(14.dp))

                PrimaryButton(
                    label = s.loginButton,
                    loading = loading,
                    onClick = {
                        if (apiKey.isBlank()) {
                            error = s.loginErrorEmpty
                            return@PrimaryButton
                        }
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                settings.saveApiKey(apiKey.trim())
                                apiClient.getAdvisorProfile()
                                onLoginSuccess()
                            } catch (_: Exception) {
                                settings.clearApiKey()
                                error = s.loginErrorInvalid
                            } finally {
                                loading = false
                            }
                        }
                    },
                )

                Spacer(Modifier.height(14.dp))

                RegisterPrompt(
                    prompt = s.loginRegisterPrompt,
                    linkLabel = s.loginRegister,
                    accent = colors.primaryAccent,
                    muted = colors.muted,
                )

                Spacer(Modifier.height(18.dp))

                TrustRow(
                    trustLabel = s.loginFooterTrust,
                    complianceLabel = s.loginFooterCompliance,
                    color = colors.muted,
                )
            }
        }
    }
}

// ──────────────────── visual primitives ────────────────────

@Composable
private fun StatusPill(label: String) {
    val colors = LocalMidasColors.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val ringAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha",
    )
    val ringSize by transition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringSize",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ringSize.dp)
                    .clip(CircleShape)
                    .background(colors.statusPositive.copy(alpha = ringAlpha)),
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.statusPositive),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
    }
}

@Composable
private fun MarketTicker() {
    val colors = LocalMidasColors.current
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            val target = scroll.maxValue
            if (target <= 0) {
                kotlinx.coroutines.delay(120)
                continue
            }
            scroll.animateScrollTo(
                value = target,
                animationSpec = tween(
                    durationMillis = (target * 28).coerceAtLeast(8000),
                    easing = LinearEasing,
                ),
            )
            scroll.scrollTo(0)
        }
    }

    val tickerBg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tickerBg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "LIVE",
            color = colors.statusPositive,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll, enabled = false),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val items = listOf(
                    TickerEntry("BMV", "56,482.11", "+0.84%", true),
                    TickerEntry("USD/MXN", "17.23", "−0.12%", false),
                    TickerEntry("CETES 28D", "10.28%", "+0.02%", true),
                    TickerEntry("TIIE 91D", "9.84%", "−0.05%", false),
                    TickerEntry("USD/COP", "4,121.30", "−0.31%", false),
                    TickerEntry("COLCAP", "1,342.55", "+0.22%", true),
                )
                // Duplicated for seamless wrap-around
                (items + items).forEach { entry ->
                    TickerCell(entry, colors)
                    Spacer(Modifier.width(28.dp))
                }
            }
        }
    }
}

private data class TickerEntry(
    val sym: String,
    val value: String,
    val delta: String,
    val up: Boolean,
)

@Composable
private fun TickerCell(entry: TickerEntry, colors: MidasColors) {
    val mono = FontFamily.Monospace
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = entry.sym,
            color = colors.muted.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontFamily = mono,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.value,
            color = colors.muted,
            fontSize = 11.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.delta,
            color = if (entry.up) colors.statusPositive else colors.statusNegative,
            fontSize = 11.sp,
            fontFamily = mono,
        )
    }
}

@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    hasError: Boolean,
) {
    val colors = LocalMidasColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var show by remember { mutableStateOf(false) }

    val borderColor = when {
        hasError -> colors.statusNegative
        focused -> colors.primaryAccent
        else -> colors.cardBorder
    }
    val fieldBg = if (colors.isDark) Color.Black.copy(alpha = 0.45f) else Color(0xFFFAFAF8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fieldBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LockGlyph(tint = colors.muted.copy(alpha = 0.6f))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (show || value.isEmpty()) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation('•')
                },
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp,
                ),
                cursorBrush = SolidColor(colors.primaryAccent),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "mk_live_•••••••••",
                            color = colors.subtleMuted,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.4.sp,
                        )
                    }
                    inner()
                },
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { show = !show },
                contentAlignment = Alignment.Center,
            ) {
                EyeGlyph(open = show, tint = colors.muted)
            }
        }
    }
}

@Composable
private fun ClipboardSuggestion(
    title: String,
    preview: String,
    action: String,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) {
        colors.primaryAccent.copy(alpha = 0.08f)
    } else {
        colors.primaryAccent.copy(alpha = 0.10f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipboardGlyph(tint = colors.primaryAccent)
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = colors.primaryAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = preview,
                color = colors.primaryAccent.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = action,
            color = colors.primaryAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.0.sp,
        )
    }
}

@Composable
private fun ErrorRow(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(
                color = color,
                radius = size.width / 2 - 1f,
                style = Stroke(width = 2f),
            )
            drawLine(
                color = color,
                start = Offset(size.width / 2, size.height * 0.32f),
                end = Offset(size.width / 2, size.height * 0.62f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = 1.2f,
                center = Offset(size.width / 2, size.height * 0.78f),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        enabled = !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryAccent,
            contentColor = colors.primaryAccentOn,
            disabledContainerColor = colors.primaryAccent.copy(alpha = 0.4f),
            disabledContentColor = colors.primaryAccentOn.copy(alpha = 0.4f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.primaryAccentOn,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.3.sp,
            )
            Spacer(Modifier.width(8.dp))
            ArrowForwardGlyph(tint = colors.primaryAccentOn)
        }
    }
}

@Composable
private fun RegisterPrompt(
    prompt: String,
    linkLabel: String,
    accent: Color,
    muted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = AnnotatedString("$prompt "),
            color = muted,
            fontSize = 12.5.sp,
        )
        Text(
            text = linkLabel,
            color = accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { /* TODO: register flow */ },
        )
    }
}

@Composable
private fun TrustRow(
    trustLabel: String,
    complianceLabel: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShieldGlyph(tint = color)
        Spacer(Modifier.width(5.dp))
        Text(
            text = trustLabel,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = complianceLabel,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
        )
    }
}

// ──────────────────── glyphs (drawn so they match the SVGs in the design) ────────────────────

@Composable
private fun LockGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        // body rect (4,10) (16x11) on 24
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h
        drawRoundRect(
            color = tint,
            topLeft = Offset(px(4f), py(10f)),
            size = Size(px(16f), py(11f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            style = Stroke(width = 1.6f),
        )
        // shackle: arc from (8,10) up to (16,10) curve to (8,7)→top
        val shackle = Path().apply {
            moveTo(px(8f), py(10f))
            lineTo(px(8f), py(7f))
            // approximate semicircle to (16,7) via cubic
            cubicTo(
                px(8f), py(2f),
                px(16f), py(2f),
                px(16f), py(7f),
            )
            lineTo(px(16f), py(10f))
        }
        drawPath(shackle, color = tint, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
    }
}

@Composable
private fun EyeGlyph(open: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h
        // Eye shape — almond
        val eye = Path().apply {
            moveTo(px(2f), py(12f))
            cubicTo(
                px(6f), py(5f),
                px(18f), py(5f),
                px(22f), py(12f),
            )
            cubicTo(
                px(18f), py(19f),
                px(6f), py(19f),
                px(2f), py(12f),
            )
            close()
        }
        drawPath(eye, color = tint, style = Stroke(width = 1.6f))
        drawCircle(
            color = tint,
            radius = px(3f),
            center = Offset(px(12f), py(12f)),
            style = Stroke(width = 1.6f),
        )
        if (!open) {
            drawLine(
                color = tint,
                start = Offset(px(3f), py(3f)),
                end = Offset(px(21f), py(21f)),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ClipboardGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h
        drawRoundRect(
            color = tint,
            topLeft = Offset(px(8f), py(4f)),
            size = Size(px(12f), py(16f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            style = Stroke(width = 1.6f),
        )
        val sheet = Path().apply {
            moveTo(px(4f), py(16f))
            lineTo(px(4f), py(6f))
            cubicTo(
                px(4f), py(5f),
                px(5f), py(4f),
                px(6f), py(4f),
            )
            lineTo(px(14f), py(4f))
        }
        drawPath(sheet, color = tint, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
    }
}

@Composable
private fun ShieldGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(11.dp)) {
        val w = size.width
        val h = size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h
        val shield = Path().apply {
            moveTo(px(12f), py(2f))
            lineTo(px(20f), py(6f))
            lineTo(px(20f), py(12f))
            cubicTo(
                px(20f), py(17f),
                px(16.5f), py(21f),
                px(12f), py(22f),
            )
            cubicTo(
                px(7.5f), py(21f),
                px(4f), py(17f),
                px(4f), py(12f),
            )
            lineTo(px(4f), py(6f))
            close()
        }
        drawPath(shield, color = tint, style = Stroke(width = 1.8f))
        // check
        val check = Path().apply {
            moveTo(px(9f), py(12f))
            lineTo(px(11f), py(14f))
            lineTo(px(15f), py(10f))
        }
        drawPath(
            check,
            color = tint,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun maskClipboard(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length <= 14) return trimmed
    return "${trimmed.take(12)}…${trimmed.takeLast(4)}"
}


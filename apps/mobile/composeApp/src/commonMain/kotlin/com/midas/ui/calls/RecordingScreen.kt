package com.midas.ui.calls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.IntentInfo
import com.midas.data.api.MidasApiClient
import com.midas.data.api.RecordingSession
import com.midas.data.repository.SettingsRepository
import com.midas.domain.model.CreditApplication
import com.midas.ui.components.ArrowBackGlyph
import com.midas.ui.components.ArrowForwardGlyph
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.permissions.rememberAudioPermissionState
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

enum class RecordingState { IDLE, RECORDING, PROCESSING, COMPLETED, ERROR }

private const val MAX_SECONDS = 80
private val RED = Color(0xFFEF5350)

@Composable
fun RecordingScreen(
    apiClient: MidasApiClient,
    settings: SettingsRepository,
    onFinished: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current

    var sessionSubject by remember { mutableStateOf("") }
    var clientNotes by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(RecordingState.IDLE) }
    var transcript by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var intent by remember { mutableStateOf<IntentInfo?>(null) }
    var callId by remember { mutableStateOf<String?>(null) }
    var generatedApp by remember { mutableStateOf<CreditApplication?>(null) }
    var generatingApp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val audioPermission = rememberAudioPermissionState()

    LaunchedEffect(state) {
        if (state == RecordingState.RECORDING) {
            elapsedSeconds = 0
            while (state == RecordingState.RECORDING) {
                delay(1000)
                elapsedSeconds++
                if (elapsedSeconds >= MAX_SECONDS) {
                    state = RecordingState.PROCESSING
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            RecHeader(
                state = state,
                onBack = onFinished,
                setupLabel = s.recordingHeaderSetup,
                liveLabel = s.recordingHeaderLive,
                resultLabel = s.recordingHeaderResult,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                when (state) {
                    RecordingState.IDLE -> SetupPhase(
                        sessionSubject = sessionSubject,
                        onSessionSubjectChange = { sessionSubject = it },
                        clientNotes = clientNotes,
                        onClientNotesChange = { clientNotes = it },
                        micGranted = audioPermission.granted,
                        error = error,
                        onStart = {
                            if (!audioPermission.granted) {
                                audioPermission.request()
                                return@SetupPhase
                            }
                            if (sessionSubject.isBlank()) {
                                error = s.recordingErrorEmptyName
                                return@SetupPhase
                            }
                            error = null
                            state = RecordingState.RECORDING
                            transcript = ""
                            intent = null
                            generatedApp = null
                            callId = null
                            scope.launch {
                                try {
                                    val session = RecordingSession(apiClient, settings)
                                    session.start(
                                        clientName = sessionSubject,
                                        onTranscript = { text, isFinal ->
                                            if (isFinal) {
                                                transcript = if (transcript.isEmpty()) text
                                                else "$transcript $text"
                                            }
                                        },
                                        onIntent = { result ->
                                            intent = result
                                            callId = result.callId ?: session.callId
                                        },
                                        onCompleted = {
                                            callId = session.callId
                                            state = RecordingState.COMPLETED
                                        },
                                        onError = { msg ->
                                            error = msg
                                            state = RecordingState.ERROR
                                        },
                                        isRecording = { state == RecordingState.RECORDING },
                                    )
                                } catch (e: Exception) {
                                    error = e.message
                                    state = RecordingState.ERROR
                                }
                            }
                        },
                    )

                    RecordingState.RECORDING, RecordingState.PROCESSING -> LivePhase(
                        subject = sessionSubject.ifBlank { s.voipUnknownClient },
                        elapsedSeconds = elapsedSeconds,
                        transcript = transcript,
                        intent = intent,
                        processing = state == RecordingState.PROCESSING,
                        onStop = { state = RecordingState.PROCESSING },
                    )

                    RecordingState.COMPLETED -> ResultPhase(
                        subject = sessionSubject.ifBlank { s.voipUnknownClient },
                        elapsedSeconds = elapsedSeconds,
                        intent = intent,
                        callId = callId,
                        generatedApp = generatedApp,
                        generatingApp = generatingApp,
                        onGenerate = {
                            val id = callId ?: return@ResultPhase
                            if (generatingApp) return@ResultPhase
                            scope.launch {
                                generatingApp = true
                                try {
                                    generatedApp = apiClient.generateApplicationFromCall(id)
                                } catch (e: Exception) {
                                    error = "Error: ${e.message}"
                                } finally {
                                    generatingApp = false
                                }
                            }
                        },
                        onBack = onFinished,
                    )

                    RecordingState.ERROR -> ErrorPhase(
                        message = error ?: s.recordingError,
                        onBack = onFinished,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────── header ───────────────────────────────────────

@Composable
private fun RecHeader(
    state: RecordingState,
    onBack: () -> Unit,
    setupLabel: String,
    liveLabel: String,
    resultLabel: String,
) {
    val colors = LocalMidasColors.current
    val (pillColor, pillText, showRedPulse) = when (state) {
        RecordingState.RECORDING, RecordingState.PROCESSING -> Triple(RED, liveLabel, true)
        RecordingState.COMPLETED -> Triple(colors.primaryAccent, resultLabel, false)
        else -> Triple(colors.muted, setupLabel, false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
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
        StatusPill(color = pillColor, label = pillText, showRedPulse = showRedPulse)
        Spacer(Modifier.size(36.dp))
    }
}

@Composable
private fun StatusPill(color: Color, label: String, showRedPulse: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showRedPulse) {
            val transition = rememberInfiniteTransition(label = "recPulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "recPulseAlpha",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.8.sp,
        )
    }
}

// ─────────────────────────────────────── SETUP ───────────────────────────────────────

@Composable
private fun SetupPhase(
    sessionSubject: String,
    onSessionSubjectChange: (String) -> Unit,
    clientNotes: String,
    onClientNotesChange: (String) -> Unit,
    micGranted: Boolean,
    error: String?,
    onStart: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent

    Spacer(Modifier.height(14.dp))

    // Hero mic with glow
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0f to accent.copy(alpha = 0.20f),
                            0.65f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.33f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = s.recordingSetupTitle,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = s.recordingSetupSubtitle,
            color = colors.muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }

    Spacer(Modifier.height(22.dp))

    // Status tiles row
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusTile(
            icon = Icons.Default.Mic,
            label = s.recordingTileMicLabel.uppercase(),
            value = if (micGranted) s.recordingMicEnabled else s.recordingPermissionNeeded,
            ok = micGranted,
            modifier = Modifier.weight(1f),
        )
        StatusTile(
            icon = Icons.Default.Lock,
            label = s.recordingTileEncryptionLabel.uppercase(),
            value = s.recordingTileEncryptionValue,
            ok = true,
            modifier = Modifier.weight(1f),
        )
    }

    NumberedSectionHeader(number = "01", title = s.recordingSectionIdentification.uppercase())

    RecTextField(
        value = sessionSubject,
        onValueChange = onSessionSubjectChange,
        placeholder = s.recordingPlaceholderName,
        leadingIcon = Icons.Default.Person,
    )
    Spacer(Modifier.height(8.dp))
    RecTextField(
        value = clientNotes,
        onValueChange = onClientNotesChange,
        placeholder = s.recordingPlaceholderNotes,
        leadingIcon = Icons.Default.Notes,
        optionalTag = s.recordingOptionalTag,
    )

    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(error, color = colors.statusNegative, fontSize = 12.sp)
    }

    Spacer(Modifier.height(18.dp))

    // Disclaimer card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (colors.isDark) MidasOrange.copy(alpha = 0.06f)
                else MidasOrange.copy(alpha = 0.08f),
            )
            .border(1.dp, MidasOrange.copy(alpha = 0.27f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MidasOrange,
            modifier = Modifier.size(14.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = s.recordingDisclaimer,
            color = colors.muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }

    Spacer(Modifier.height(18.dp))

    // Start button
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = colors.primaryAccentOn,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.primaryAccentOn),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = s.recordingStart,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 1.5.sp,
        )
    }

    Spacer(Modifier.height(10.dp))

    Text(
        text = s.recordingMaxFooter,
        color = colors.muted,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.8.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusTile(
    icon: ImageVector,
    label: String,
    value: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val color = if (ok) colors.primaryAccent else MidasOrange
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.0.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun NumberedSectionHeader(number: String, title: String) {
    val colors = LocalMidasColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$number /",
            color = colors.primaryAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.0.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
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
}

@Composable
private fun RecTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    optionalTag: String? = null,
) {
    val colors = LocalMidasColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) colors.primaryAccent else colors.cardBorder
    val bg = if (colors.isDark) Color.Black.copy(alpha = 0.4f) else Color(0xFFFAFAF8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            leadingIcon,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(colors.primaryAccent),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.muted,
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                },
            )
        }
        if (optionalTag != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = optionalTag,
                color = colors.muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// ─────────────────────────────────────── LIVE ───────────────────────────────────────

@Composable
private fun LivePhase(
    subject: String,
    elapsedSeconds: Int,
    transcript: String,
    intent: IntentInfo?,
    processing: Boolean,
    onStop: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val progress = (elapsedSeconds.toFloat() / MAX_SECONDS).coerceIn(0f, 1f)

    Spacer(Modifier.height(10.dp))

    // Timer card (red-tinted)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (colors.isDark) RED.copy(alpha = 0.04f) else RED.copy(alpha = 0.05f),
            )
            .border(1.dp, RED.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${s.recordingLiveLabel} · ${subject.uppercase()}",
                color = RED,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = s.recordingLiveMax,
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(10.dp))
        val mm = (elapsedSeconds / 60).toString().padStart(2, '0')
        val ss = (elapsedSeconds % 60).toString().padStart(2, '0')
        Text(
            text = "$mm:$ss",
            color = colors.textPrimary,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(14.dp))
        Waveform(progress = progress, elapsedSeconds = elapsedSeconds)
        Spacer(Modifier.height(12.dp))
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.cardBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(accent),
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    // Intent analyzer card
    IntentAnalyzingCard(
        elapsedSeconds = elapsedSeconds,
        intent = intent,
        waitLabel = s.recordingLiveHintWait,
        signalsLabel = s.recordingLiveHintSignals,
        likelyPrefix = s.recordingLiveHintLikely,
        analyzingLabel = s.recordingAnalyzingIntent,
    )

    NumberedSectionHeader(
        number = "01",
        title = s.recordingSectionLiveTranscript.uppercase(),
    )

    // Transcript card
    val transcriptBg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(transcriptBg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        if (transcript.isBlank()) {
            BlinkingCaret(
                prefix = s.recordingWaitingAudio,
                italic = true,
            )
        } else {
            TranscriptRunning(text = transcript, processing = processing)
        }
    }

    Spacer(Modifier.height(16.dp))

    // Stop button
    Button(
        onClick = onStop,
        enabled = !processing,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (colors.isDark) RED.copy(alpha = 0.08f) else RED.copy(alpha = 0.10f),
            contentColor = RED,
            disabledContainerColor = RED.copy(alpha = 0.04f),
            disabledContentColor = RED.copy(alpha = 0.5f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, RED.copy(alpha = 0.33f)),
    ) {
        if (processing) {
            CircularProgressIndicator(
                color = RED,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = s.recordingAnalyzing,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RED),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = s.recordingStopAnalyze,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun Waveform(progress: Float, elapsedSeconds: Int) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bars = 48
        repeat(bars) { i ->
            val raw = abs(sin(i * 0.9f + elapsedSeconds * 0.8f))
            val h = (10f + raw * 22f).dp
            val isPast = i.toFloat() / bars < progress
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isPast) accent else colors.cardBorder),
            )
        }
    }
}

@Composable
private fun IntentAnalyzingCard(
    elapsedSeconds: Int,
    intent: IntentInfo?,
    waitLabel: String,
    signalsLabel: String,
    likelyPrefix: String,
    analyzingLabel: String,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = if (colors.isDark) accent.copy(alpha = 0.06f) else accent.copy(alpha = 0.08f)

    val hint = when {
        intent?.intentDetected == true && !intent.productType.isNullOrBlank() ->
            "$likelyPrefix: ${intent.productType}"
        elapsedSeconds < 8 -> waitLabel
        else -> signalsLabel
    }
    val confidencePct = intent?.confidence?.let { (it * 100).toInt() }
        ?: (20 + elapsedSeconds * 2).coerceAtMost(92)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.27f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pulsing dot
        val transition = rememberInfiniteTransition(label = "intentPulse")
        val dotAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "intentPulseAlpha",
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = analyzingLabel,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                color = colors.textPrimary,
                fontSize = 12.sp,
            )
        }
        Text(
            text = "$confidencePct%",
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun BlinkingCaret(prefix: String, italic: Boolean) {
    val colors = LocalMidasColors.current
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = prefix,
            color = colors.muted,
            fontSize = 12.sp,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        )
        Text(
            text = "▎",
            color = colors.primaryAccent.copy(alpha = alpha),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TranscriptRunning(text: String, processing: Boolean) {
    val colors = LocalMidasColors.current
    val transition = rememberInfiniteTransition(label = "caret2")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha2",
    )
    val content = buildAnnotatedString {
        append(text)
        if (!processing) {
            withStyle(SpanStyle(color = colors.primaryAccent.copy(alpha = alpha))) {
                append(" ▎")
            }
        }
    }
    Text(
        text = content,
        color = colors.textPrimary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

// ─────────────────────────────────────── RESULT ───────────────────────────────────────

@Composable
private fun ResultPhase(
    subject: String,
    elapsedSeconds: Int,
    intent: IntentInfo?,
    callId: String?,
    generatedApp: CreditApplication?,
    generatingApp: Boolean,
    onGenerate: () -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent

    val mm = (elapsedSeconds / 60).toString().padStart(2, '0')
    val ss = (elapsedSeconds % 60).toString().padStart(2, '0')
    val detected = intent?.intentDetected == true

    Spacer(Modifier.height(10.dp))

    // Hero gradient card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.08f),
                    1f to accent.copy(alpha = 0.02f),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            text = "✓ ${s.recordingHeaderResult.uppercase()} · $mm:$ss",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append(s.recordingResultHero)
                append(" ")
                withStyle(SpanStyle(color = accent)) {
                    append(s.recordingDetectedLabel)
                }
            },
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$subject · ${s.recordingResultSubtitle}",
            color = colors.muted,
            fontSize = 13.sp,
        )
    }

    Spacer(Modifier.height(14.dp))

    // Metrics grid
    val confidence = intent?.confidence?.let { "${(it * 100).toInt()}%" } ?: "—"
    val product = intent?.productType?.take(10) ?: "—"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultMetric(
            label = s.recordingMetricConfidence,
            value = confidence,
            accent = if (detected) accent else colors.muted,
            modifier = Modifier.weight(1f),
        )
        ResultMetric(
            label = s.recordingMetricProduct,
            value = product,
            accent = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        ResultMetric(
            label = s.recordingMetricAmount,
            value = "—",
            accent = MidasOrange,
            modifier = Modifier.weight(1f),
        )
    }

    NumberedSectionHeader(number = "01", title = s.recordingSectionSummary.uppercase())

    // Intent summary card
    val summaryBg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(summaryBg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = intent?.summary?.takeIf { it.isNotBlank() }
                ?: s.recordingNoIntent,
            color = colors.textPrimary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }

    NumberedSectionHeader(number = "02", title = s.recordingSectionNextStep.uppercase())

    // CTA
    if (generatedApp != null) {
        GeneratedAppCard(app = generatedApp)
        Spacer(Modifier.height(10.dp))
    } else {
        GenerateCtaButton(
            label = s.recordingGenerateApp,
            subtitle = s.recordingGenerateSubtitle,
            loading = generatingApp,
            enabled = callId != null && detected,
            onClick = onGenerate,
        )
        Spacer(Modifier.height(10.dp))
    }

    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
    ) {
        Text(
            text = s.recordingBackToCalls,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
    }

    if (callId != null) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = "${s.recordingCallIdPrefix} · ${callId.take(12).uppercase()} · ${s.recordingProcessedIn} $mm:$ss",
            color = colors.muted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultMetric(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.2).sp,
        )
    }
}

@Composable
private fun GenerateCtaButton(
    label: String,
    subtitle: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) accent else accent.copy(alpha = 0.3f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = colors.primaryAccentOn,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = colors.primaryAccentOn,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.primaryAccentOn,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.primaryAccentOn.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        ArrowForwardGlyph(tint = colors.primaryAccentOn, size = 16.dp)
    }
}

@Composable
private fun GeneratedAppCard(app: CreditApplication) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = if (colors.isDark) accent.copy(alpha = 0.06f) else accent.copy(alpha = 0.08f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            text = app.applicant.fullName,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        val product = app.productRequest.productLabel ?: app.productRequest.productType
        val amount = app.productRequest.amount
        val term = app.productRequest.term
        val line = listOfNotNull(product, amount, term).joinToString(" · ")
        if (line.isNotBlank()) {
            Text(
                text = line,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (app.conversationSummary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = app.conversationSummary,
                color = colors.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

// ─────────────────────────────────────── ERROR ───────────────────────────────────────

@Composable
private fun ErrorPhase(message: String, onBack: () -> Unit) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    Spacer(Modifier.height(40.dp))
    Text(
        text = s.recordingError,
        color = colors.statusNegative,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = message,
        color = colors.muted,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryAccent,
            contentColor = colors.primaryAccentOn,
        ),
    ) {
        Text(
            text = s.recordingBackToCalls,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Suppress("unused")
private val _anchor: AnnotatedString = AnnotatedString("")

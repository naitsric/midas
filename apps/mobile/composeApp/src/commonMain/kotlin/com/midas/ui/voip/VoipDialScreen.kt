package com.midas.ui.voip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CallDetail
import com.midas.ui.components.MidasBackdrop
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasOrange
import com.midas.voip.VoipCall
import com.midas.voip.VoipCallManager
import com.midas.voip.VoipCallState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { Dial, InCall, Post }

private data class EndedCall(
    val number: String,
    val name: String?,
    val durationSeconds: Int,
)

@Composable
fun VoipDialScreen(
    voipCallManager: VoipCallManager,
    apiClient: MidasApiClient,
    onClose: () -> Unit,
    onOpenCallDetail: (callId: String) -> Unit = {},
) {
    val active by voipCallManager.activeCall.collectAsState()
    var lastEnded by remember { mutableStateOf<EndedCall?>(null) }
    var inCallSeconds by remember { mutableStateOf(0) }
    var prefillNumber by remember { mutableStateOf("+52") }
    var prefillName by remember { mutableStateOf("") }
    var baselineCallIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    // Track the in-call timer so we can carry duration to post-call.
    LaunchedEffect(active?.callId, active?.state) {
        if (active != null && active?.state != VoipCallState.ENDED && active?.state != VoipCallState.FAILED) {
            inCallSeconds = 0
            while (true) {
                delay(1000)
                inCallSeconds += 1
            }
        }
    }

    // When a connected call clears, snapshot it as the post-call summary.
    var prevState by remember { mutableStateOf<VoipCallState?>(null) }
    LaunchedEffect(active) {
        val current = active
        if (current == null && prevState == VoipCallState.CONNECTED) {
            // Capture the call we just had — needs the previous active value;
            // we keep the captured info next to inCallSeconds.
            // (lastEnded was actually populated from the explicit hangup path below;
            // this branch is a safety net.)
        }
        prevState = current?.state
    }

    val phase = when {
        lastEnded != null && active == null -> Phase.Post
        active != null -> Phase.InCall
        else -> Phase.Dial
    }

    when (phase) {
        Phase.Dial -> DialpadPhase(
            initialNumber = prefillNumber,
            initialName = prefillName,
            onClose = onClose,
            onCall = { number, name ->
                prefillNumber = number
                prefillName = name
                scope.launch {
                    baselineCallIds = runCatching {
                        apiClient.listCalls().map { it.id }.toSet()
                    }.getOrDefault(emptySet())
                    voipCallManager.dial(toNumber = number, clientName = name.ifBlank { null })
                }
            },
        )
        Phase.InCall -> {
            val current = active ?: return
            InCallPhase(
                call = current,
                seconds = inCallSeconds,
                onMuteToggle = { voipCallManager.setMuted(!current.muted) },
                onHangup = {
                    lastEnded = EndedCall(
                        number = current.remoteNumber,
                        name = current.displayName,
                        durationSeconds = inCallSeconds,
                    )
                    voipCallManager.hangup()
                },
            )
        }
        Phase.Post -> {
            val ended = lastEnded ?: return
            PostCallPhase(
                apiClient = apiClient,
                ended = ended,
                baselineCallIds = baselineCallIds,
                onClose = {
                    lastEnded = null
                    onClose()
                },
                onAgain = { lastEnded = null },
                onOpenCallDetail = { id ->
                    lastEnded = null
                    onOpenCallDetail(id)
                },
            )
        }
    }
}

// ───────────────────────────────────────── DIAL PHASE ─────────────────────────────────────────

@Composable
private fun DialpadPhase(
    initialNumber: String,
    initialName: String,
    onClose: () -> Unit,
    onCall: (number: String, name: String) -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    var number by remember { mutableStateOf(initialNumber) }
    var name by remember { mutableStateOf(initialName) }
    var recording by remember { mutableStateOf(true) }
    val canCall = number.startsWith("+") && number.length >= 8

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconBoxButton(icon = Icons.Default.Close, onClick = onClose)
                Text(
                    text = s.voipNewCall,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(36.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Name input (transparent, underline)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(colors.primaryAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) {
                            Text(
                                text = s.voipDialClientName,
                                color = colors.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        inner()
                    }
                },
            )
            HorizontalDivider(color = colors.cardBorder)

            // Number display + country
            Spacer(Modifier.height(14.dp))
            Text(
                text = number.ifEmpty { "+—" },
                color = if (number.isEmpty()) colors.muted else colors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.3).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = countryFor(number, s.voipCountryMX, s.voipCountryCO, s.voipCountryUS, s.voipCountryE164),
                color = colors.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.0.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            // Dialpad grid
            DialpadGrid(
                onPress = { key ->
                    number = if (key == "del") {
                        if (number.length > 1) number.dropLast(1) else number
                    } else {
                        number + key
                    }
                },
                onLongPress0 = { number = "$number+" },
            )

            Spacer(Modifier.height(14.dp))

            RecordToggleCard(
                title = s.voipDialRecord,
                subtitle = s.voipDialRecordSub,
                checked = recording,
                onToggle = { recording = !recording },
            )

            Spacer(Modifier.height(12.dp))

            // Call + delete row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onCall(number.trim(), name.trim()) },
                    enabled = canCall,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = colors.primaryAccentOn,
                        disabledContainerColor = colors.primaryAccent.copy(alpha = 0.4f),
                        disabledContentColor = colors.primaryAccentOn.copy(alpha = 0.4f),
                    ),
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = s.voipDialCallButton,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (colors.isDark) Color.White.copy(alpha = 0.04f)
                            else Color.Black.copy(alpha = 0.04f),
                        )
                        .border(1.dp, colors.cardBorder, CircleShape)
                        .clickable {
                            number = if (number.length > 1) number.dropLast(1) else number
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialpadGrid(onPress: (String) -> Unit, onLongPress0: () -> Unit) {
    val keys = listOf(
        "1" to "", "2" to "ABC", "3" to "DEF",
        "4" to "GHI", "5" to "JKL", "6" to "MNO",
        "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to "",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, sub) ->
                    DialKey(
                        key = key,
                        sub = sub,
                        onPress = { onPress(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialKey(
    key: String,
    sub: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onPress),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = key,
            color = colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp,
        )
        if (sub.isNotEmpty()) {
            Text(
                text = sub,
                color = colors.muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.0.sp,
            )
        }
    }
}

@Composable
private fun RecordToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = if (checked) accent.copy(alpha = 0.10f) else
        if (colors.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.03f)
    val borderColor = if (checked) accent.copy(alpha = 0.40f) else colors.cardBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) accent.copy(alpha = 0.20f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.FlashOn,
                contentDescription = null,
                tint = if (checked) accent else colors.muted,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = colors.muted,
                fontSize = 10.sp,
            )
        }
        // Switch (manual minimal version)
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) accent else colors.cardBorder),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

// ───────────────────────────────────────── IN-CALL PHASE ─────────────────────────────────────────

@Composable
private fun InCallPhase(
    call: VoipCall,
    seconds: Int,
    onMuteToggle: () -> Unit,
    onHangup: () -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    var keypad by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    val displayName = call.displayName?.takeIf { it.isNotBlank() } ?: s.voipUnknownClient
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "C"

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        // Ambient pulse halo behind avatar
        val transition = rememberInfiniteTransition(label = "halo")
        val haloScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloScale",
        )
        val haloAlpha by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloAlpha",
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
                .size((320 * haloScale).dp)
                .background(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = 0.13f * haloAlpha),
                        0.6f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            // Top: REC badge + MIDAS · E2E
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RecBadge(label = s.voipInCallRec)
                Text(
                    text = s.voipInCallE2E,
                    color = colors.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(50.dp))

            // Avatar + name + number + timer
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                0f to accent,
                                1f to colors.primaryAccent.copy(alpha = 0.7f),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        color = colors.primaryAccentOn,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = displayName,
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = call.remoteNumber,
                    color = colors.muted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(16.dp))
                TimerPill(seconds = seconds)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stateLabel(call.state, s),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallControl(
                    label = s.voipCtrlMute,
                    icon = if (call.muted) Icons.Default.MicOff else Icons.Default.Mic,
                    active = call.muted,
                    onClick = onMuteToggle,
                )
                CallControl(
                    label = s.voipCtrlKeypad,
                    icon = Icons.Default.Apps,
                    active = keypad,
                    onClick = { keypad = !keypad },
                )
                CallControl(
                    label = s.voipCtrlSpeaker,
                    icon = Icons.Default.VolumeUp,
                    active = speaker,
                    onClick = { speaker = !speaker },
                )
            }

            // Hang up
            Button(
                onClick = onHangup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(31.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF5350),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = s.voipHangup,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun RecBadge(label: String) {
    val red = Color(0xFFEF5350)
    val transition = rememberInfiniteTransition(label = "rec")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(red.copy(alpha = 0.13f))
            .border(1.dp, red.copy(alpha = 0.40f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(red.copy(alpha = pulse)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = red,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TimerPill(seconds: Int) {
    val colors = LocalMidasColors.current
    val mm = (seconds / 60).toString().padStart(2, '0')
    val ss = (seconds % 60).toString().padStart(2, '0')
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f),
            )
            .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$mm:$ss",
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun CallControl(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMidasColors.current
    val activeBg = if (colors.isDark) Color.White else colors.textPrimary
    val activeContent = if (colors.isDark) Color.Black else Color.White
    val inactiveBg = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val bg = if (active) activeBg else inactiveBg
    val contentColor = if (active) activeContent else colors.textPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(bg)
                .then(
                    if (active) Modifier
                    else Modifier.border(1.dp, colors.cardBorder, CircleShape),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ───────────────────────────────────────── POST-CALL PHASE ─────────────────────────────────────────

private sealed interface ProcessingState {
    object Polling : ProcessingState
    data class Ready(val detail: CallDetail) : ProcessingState
    object Timeout : ProcessingState
}

@Composable
private fun PostCallPhase(
    apiClient: MidasApiClient,
    ended: EndedCall,
    baselineCallIds: Set<String>,
    onClose: () -> Unit,
    onAgain: () -> Unit,
    onOpenCallDetail: (String) -> Unit,
) {
    val s = LocalStrings.current
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val mm = (ended.durationSeconds / 60).toString().padStart(2, '0')
    val ss = (ended.durationSeconds % 60).toString().padStart(2, '0')
    val displayName = ended.name?.takeIf { it.isNotBlank() } ?: s.voipUnknownClient

    var state by remember { mutableStateOf<ProcessingState>(ProcessingState.Polling) }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Poll listCalls until a new call (not in baseline) shows up, then poll its
    // detail until the pipeline reports the transcript is ready.
    LaunchedEffect(baselineCallIds) {
        val pollIntervalMs = 3_000L
        val maxAttempts = 60 // ~3 min total (60 * 3s)
        var attempt = 0
        var newCallId: String? = null

        // Phase 1: find the new call record in listCalls.
        while (newCallId == null && attempt < maxAttempts) {
            val summaries = runCatching { apiClient.listCalls() }.getOrNull().orEmpty()
            newCallId = summaries.firstOrNull { it.id !in baselineCallIds }?.id
            if (newCallId == null) {
                delay(pollIntervalMs)
                attempt += 1
            }
        }

        if (newCallId == null) {
            state = ProcessingState.Timeout
            return@LaunchedEffect
        }

        // Phase 2: poll the detail until status == completed.
        while (attempt < maxAttempts) {
            val detail = runCatching { apiClient.getCall(newCallId) }.getOrNull()
            if (detail != null) {
                val status = detail.status.lowercase()
                if (status == "completed" && detail.transcript.isNotBlank()) {
                    state = ProcessingState.Ready(detail)
                    return@LaunchedEffect
                }
            }
            delay(pollIntervalMs)
            attempt += 1
        }
        state = ProcessingState.Timeout
    }

    val ready = (state as? ProcessingState.Ready)?.detail
    val wordCount = ready?.transcript?.let { countWords(it) }

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {
        MidasBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconBoxButton(icon = Icons.Default.Close, onClick = onClose)
                Text(
                    text = s.voipPostCallTitle,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.size(36.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Hero summary
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.13f))
                        .border(2.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = displayName,
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${ended.number} · $mm:$ss",
                    color = colors.muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(22.dp))

            // Stats trio
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(label = s.voipPostStatDuration, value = "$mm:$ss", modifier = Modifier.weight(1f))
                StatTile(
                    label = s.voipPostStatWords,
                    value = wordCount?.toString() ?: "—",
                    modifier = Modifier.weight(1f),
                )
                StatTile(label = s.voipPostStatSpeakers, value = "—", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            when (val st = state) {
                is ProcessingState.Polling -> ProcessingCard(
                    title = s.voipPostProcessing,
                    subtitle = s.voipPostProcessingSub,
                )

                is ProcessingState.Ready -> {
                    Text(
                        text = s.voipPostTranscriptHeader,
                        color = colors.muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TranscriptExcerpt(text = st.detail.transcript)
                }

                is ProcessingState.Timeout -> TimeoutCard(message = s.voipPostTimeout)
            }

            Spacer(Modifier.height(20.dp))

            // Primary CTA — only enabled when processing completed.
            val readyDetail = (state as? ProcessingState.Ready)?.detail
            Button(
                onClick = {
                    if (readyDetail == null) return@Button
                    scope.launch {
                        generating = true
                        try {
                            apiClient.generateApplicationFromCall(readyDetail.id)
                        } catch (_: Exception) {
                        } finally {
                            generating = false
                        }
                        onOpenCallDetail(readyDetail.id)
                    }
                },
                enabled = readyDetail != null && !generating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = colors.primaryAccentOn,
                    disabledContainerColor = accent.copy(alpha = 0.35f),
                    disabledContentColor = colors.primaryAccentOn.copy(alpha = 0.5f),
                ),
            ) {
                if (generating) {
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
                        text = s.voipPostGenerate,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }

            if (readyDetail != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onOpenCallDetail(readyDetail.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = s.voipPostOpenDetail,
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onAgain,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.muted),
            ) {
                Text(
                    text = s.voipPostLater,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ProcessingCard(title: String, subtitle: String) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = if (colors.isDark) accent.copy(alpha = 0.06f) else accent.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = accent,
            strokeWidth = 2.dp,
        )
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
                text = subtitle,
                color = colors.muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun TimeoutCard(message: String) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) MidasOrange.copy(alpha = 0.08f) else MidasOrange.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, MidasOrange.copy(alpha = 0.33f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = colors.textPrimary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun TranscriptExcerpt(text: String) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.02f) else Color.Black.copy(alpha = 0.02f)
    val excerpt = text
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        excerpt.forEach { line ->
            Text(
                text = line,
                color = colors.textPrimary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

private fun countWords(text: String): Int =
    text.split(Regex("\\s+")).count { it.isNotBlank() }

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
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
            text = value,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.2).sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label.uppercase(),
            color = colors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.0.sp,
        )
    }
}

// ───────────────────────────────────────── shared bits ─────────────────────────────────────────

@Composable
private fun IconBoxButton(icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalMidasColors.current
    val bg = if (colors.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
    }
}

private fun countryFor(
    number: String,
    mx: String,
    co: String,
    us: String,
    intl: String,
): String = when {
    number.startsWith("+52") -> mx
    number.startsWith("+57") -> co
    number.startsWith("+1") -> us
    else -> intl
}

private fun stateLabel(state: VoipCallState, s: com.midas.ui.i18n.Strings): String = when (state) {
    VoipCallState.CONNECTING -> s.voipInCallConnecting
    VoipCallState.RINGING -> s.voipInCallRinging
    VoipCallState.CONNECTED -> s.voipInCallConnected
    VoipCallState.ENDED -> ""
    VoipCallState.FAILED -> ""
}

@Suppress("unused")
private val unusedKeyboardImport = KeyboardType.Phone

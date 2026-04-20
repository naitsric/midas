package com.midas.ui.calls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.IntentInfo
import com.midas.data.api.MidasApiClient
import com.midas.data.api.RecordingSession
import com.midas.data.repository.SettingsRepository
import com.midas.domain.model.CreditApplication
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.permissions.rememberAudioPermissionState
import com.midas.ui.theme.*
import kotlinx.coroutines.*

enum class RecordingState {
    IDLE, RECORDING, PROCESSING, COMPLETED, ERROR
}

@Composable
fun RecordingScreen(
    apiClient: MidasApiClient,
    settings: SettingsRepository,
    onFinished: () -> Unit,
) {
    val s = LocalStrings.current
    var sessionSubject by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(RecordingState.IDLE) }
    var transcript by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0) }
    var intent by remember { mutableStateOf<IntentInfo?>(null) }
    var callId by remember { mutableStateOf<String?>(null) }
    var generatedApp by remember { mutableStateOf<CreditApplication?>(null) }
    var generatingApp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val audioPermission = rememberAudioPermissionState()

    val maxSeconds = 80

    LaunchedEffect(state) {
        if (state == RecordingState.RECORDING) {
            elapsedSeconds = 0
            while (state == RecordingState.RECORDING) {
                delay(1000)
                elapsedSeconds++
                if (elapsedSeconds >= maxSeconds) {
                    state = RecordingState.PROCESSING
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidasDarkBg),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onFinished) {
                Icon(Icons.Default.ArrowBack, contentDescription = s.back, tint = MidasGreen)
            }
            Text(
                s.recordingTitle,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state == RecordingState.IDLE) {
                // === SETUP VIEW ===
                Spacer(Modifier.height(16.dp))

                // Mic icon with glow
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Glow circle
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MidasGreen.copy(alpha = 0.08f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(MidasGreen.copy(alpha = 0.12f)),
                    )
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MidasGreen,
                        modifier = Modifier.size(40.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    s.recordingSetupTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    s.recordingSetupSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MidasGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(24.dp))

                // Status card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MidasDarkCardBorder,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .background(
                            color = MidasDarkCard.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(16.dp),
                ) {
                    Column {
                        // Mic access row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MidasGray,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    s.recordingMicAccess,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MidasGray,
                                )
                            }
                            Surface(
                                color = if (audioPermission.granted) MidasGreen.copy(alpha = 0.12f) else MidasOrange.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    if (audioPermission.granted) s.recordingMicEnabled else "PERMITIR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (audioPermission.granted) MidasGreen else MidasOrange,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Secure connection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MidasGray.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                s.recordingSecureConnection,
                                style = MaterialTheme.typography.labelSmall,
                                color = MidasGray.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Session subject field
                TextField(
                    value = sessionSubject,
                    onValueChange = { sessionSubject = it },
                    placeholder = {
                        Text(
                            s.recordingSessionSubject,
                            color = MidasGray.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            letterSpacing = 1.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MidasDarkCard,
                        unfocusedContainerColor = MidasDarkCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MidasGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(12.dp))

                // Client reference field
                TextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    placeholder = {
                        Text(
                            "${s.recordingClientName} (OPCIONAL)",
                            color = MidasGray.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            letterSpacing = 1.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MidasDarkCard,
                        unfocusedContainerColor = MidasDarkCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MidasGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                error?.let {
                    Text(
                        it,
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Start button
                Button(
                    onClick = {
                        if (!audioPermission.granted) {
                            audioPermission.request()
                            return@Button
                        }
                        if (sessionSubject.isBlank()) {
                            error = s.recordingErrorEmptyName
                            return@Button
                        }
                        val nameForCall = clientName.ifBlank { sessionSubject }
                        state = RecordingState.RECORDING
                        error = null
                        scope.launch {
                            try {
                                val session = RecordingSession(apiClient, settings)
                                session.start(
                                    clientName = nameForCall,
                                    onTranscript = { text, isFinal ->
                                        if (isFinal) {
                                            transcript = if (transcript.isEmpty()) text else "$transcript $text"
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MidasGreen,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        s.recordingStart,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Disclaimer
                Text(
                    s.recordingDisclaimer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MidasGray.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                Spacer(Modifier.height(24.dp))
            } else {
                // === RECORDING / PROCESSING / COMPLETED VIEW ===
                Spacer(Modifier.height(16.dp))

                if (state == RecordingState.RECORDING) {
                    // Live indicator + timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Pulsing dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF5350)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            s.recordingLive,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Timer
                    val minutes = elapsedSeconds / 60
                    val secs = elapsedSeconds % 60
                    Text(
                        text = "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 4.sp,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { elapsedSeconds.toFloat() / maxSeconds.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MidasGreen,
                        trackColor = MidasDarkCard,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            sessionSubject,
                            style = MaterialTheme.typography.bodySmall,
                            color = MidasGray,
                        )
                        Text(
                            "1:20 max",
                            style = MaterialTheme.typography.bodySmall,
                            color = MidasGray.copy(alpha = 0.5f),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Stop button
                    Button(
                        onClick = { state = RecordingState.PROCESSING },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            s.recordingStop,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }

                if (state == RecordingState.PROCESSING) {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(color = MidasGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        s.recordingAnalyzing,
                        style = MaterialTheme.typography.titleMedium,
                        color = MidasGray,
                    )
                    Spacer(Modifier.height(32.dp))
                }

                // Transcript
                if (transcript.isNotEmpty()) {
                    Text(
                        s.recordingTranscription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MidasGreen,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Intent result
                intent?.let { intentInfo ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (intentInfo.isActionable) MidasGreen.copy(alpha = 0.1f) else MidasDarkCard,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = if (intentInfo.isActionable) BorderStroke(1.dp, MidasGreen.copy(alpha = 0.3f)) else null,
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (intentInfo.intentDetected) s.recordingIntentDetected else s.recordingNoIntent,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (intentInfo.intentDetected) MidasGreen else MidasGray,
                                )
                                Text(
                                    "${(intentInfo.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MidasGray,
                                )
                            }
                            intentInfo.productType?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${s.recordingProduct}: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MidasGreen,
                                )
                            }
                            intentInfo.summary?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MidasGray)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (intentInfo.isActionable && generatedApp == null) {
                        Button(
                            onClick = {
                                val id = callId ?: return@Button
                                generatingApp = true
                                scope.launch {
                                    try {
                                        generatedApp = apiClient.generateApplicationFromCall(id)
                                    } catch (e: Exception) {
                                        error = "Error: ${e.message}"
                                    } finally {
                                        generatingApp = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !generatingApp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MidasGreen,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (generatingApp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (generatingApp) s.recordingGenerating else s.recordingGenerateApp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Generated app
                generatedApp?.let { app ->
                    Text(
                        s.recordingGeneratedApp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MidasGreen,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(app.applicant.fullName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            app.productRequest.productLabel?.let {
                                Text("${s.recordingProduct}: $it", style = MaterialTheme.typography.bodyMedium, color = MidasGreen)
                            }
                            app.productRequest.amount?.let {
                                Text("${s.applicationAmount}: $it", style = MaterialTheme.typography.bodyMedium, color = MidasGray)
                            }
                            app.productRequest.term?.let {
                                Text("${s.applicationTerm}: $it", style = MaterialTheme.typography.bodyMedium, color = MidasGray)
                            }
                            app.productRequest.location?.let {
                                Text("${s.applicationLocation}: $it", style = MaterialTheme.typography.bodyMedium, color = MidasGray)
                            }
                            app.applicant.estimatedIncome?.let {
                                Text("${s.applicationIncome}: $it", style = MaterialTheme.typography.bodyMedium, color = MidasGray)
                            }
                            app.applicant.employmentType?.let {
                                Text("${s.applicationEmployment}: $it", style = MaterialTheme.typography.bodyMedium, color = MidasGray)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(app.conversationSummary, style = MaterialTheme.typography.bodySmall, color = MidasGray)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                error?.let {
                    Text(
                        it,
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (state == RecordingState.COMPLETED || state == RecordingState.ERROR) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MidasGreen,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(s.recordingBackToCalls, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

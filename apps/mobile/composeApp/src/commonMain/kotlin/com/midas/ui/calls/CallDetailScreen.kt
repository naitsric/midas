package com.midas.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CallDetail
import com.midas.domain.model.CreditApplication
import com.midas.domain.model.IntentResult
import com.midas.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CallDetailScreen(
    apiClient: MidasApiClient,
    callId: String,
    onBack: () -> Unit,
    onApplicationGenerated: (CreditApplication) -> Unit = {},
) {
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
                        // Silent — UI shows transcript even without intent
                    } finally {
                        intentLoading = false
                    }
                }
            } catch (e: Exception) {
                loadError = e.message ?: "Error al cargar llamada"
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidasDarkBg),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Detalle de llamada",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MidasGreen)
            }
            return@Column
        }

        if (loadError != null || call == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    loadError ?: "Llamada no encontrada",
                    color = Color(0xFFEF5350),
                )
            }
            return@Column
        }

        val c = call!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Header card with client + duration + status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        c.clientName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = statusColor(c.status).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                c.status.uppercase(),
                                color = statusColor(c.status),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        c.durationSeconds?.let { sec ->
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${sec / 60}m ${sec % 60}s",
                                color = MidasGray,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Transcript section
            Text(
                "Transcripción",
                color = MidasGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = c.transcript.ifBlank { "(Sin transcripción aún)" },
                    color = if (c.transcript.isBlank()) MidasGray else Color.White,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Intent detection card
            if (intentLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MidasGreen,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Analizando intención...", color = MidasGray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))
            } else intent?.let { i ->
                IntentCard(i)
                Spacer(Modifier.height(20.dp))
            }

            // CTA: generate application
            if (c.status == "completed" && c.transcript.isNotBlank()) {
                if (generatedApp != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidasGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Solicitud generada",
                                color = MidasGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                generatedApp!!.statusLabel ?: generatedApp!!.status,
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                            generatedApp!!.productRequest?.summary?.let { summary ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    summary,
                                    color = MidasGray,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                generating = true
                                generationError = null
                                try {
                                    val app = apiClient.generateApplicationFromCall(c.id)
                                    generatedApp = app
                                    onApplicationGenerated(app)
                                } catch (e: Exception) {
                                    generationError = e.message ?: "Error generando solicitud"
                                } finally {
                                    generating = false
                                }
                            }
                        },
                        enabled = !generating,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MidasGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = MidasGreen.copy(alpha = 0.3f),
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (generating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Generar solicitud de crédito",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                    generationError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFEF5350), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun statusColor(status: String): Color = when (status) {
    "completed" -> MidasGreen
    "recording", "processing" -> MidasOrange
    else -> MidasBlue
}

@Composable
private fun IntentCard(intent: IntentResult) {
    val pct = (intent.confidence * 100).toInt()
    val accent = when {
        !intent.intentDetected -> MidasGray
        intent.isActionable -> MidasGreen
        else -> MidasOrange
    }
    val title = if (intent.intentDetected) "Intención detectada" else "Sin intención clara"

    Text(
        "Análisis IA",
        color = MidasGray,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "$pct%",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            intent.productType?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Producto: $it",
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
            if (intent.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    intent.summary,
                    color = MidasGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            val entries = intent.entities.filter { (_, v) -> !v.isNullOrBlank() }
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                entries.forEach { (k, v) ->
                    Text(
                        "• ${k.replaceFirstChar { it.uppercase() }}: $v",
                        color = MidasGray,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

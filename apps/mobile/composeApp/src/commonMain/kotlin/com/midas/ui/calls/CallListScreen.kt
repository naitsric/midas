package com.midas.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CallSummary
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CallListScreen(
    apiClient: MidasApiClient,
    onNewRecording: () -> Unit,
    onCallClick: (String) -> Unit = {},
) {
    val s = LocalStrings.current
    var calls by remember { mutableStateOf<List<CallSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidasDarkBg)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            s.callsTitle,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MidasGreen)
            }
        } else if (calls.isEmpty()) {
            Text(s.callsEmpty, color = MidasGray, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calls) { call ->
                    CallCard(call, onClick = {
                        println("[CallList] tap on call=${call.id}")
                        onCallClick(call.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun CallCard(call: CallSummary, onClick: () -> Unit) {
    val statusColor = when (call.status) {
        "completed" -> MidasGreen
        "recording" -> MidasOrange
        else -> MidasBlue
    }

    Surface(
        onClick = {
            println("[CallCard] Surface onClick fired")
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        color = MidasDarkCard,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MidasPurple.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MidasPurple,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    call.clientName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                call.durationSeconds?.let { seconds ->
                    Text(
                        "${seconds / 60}m ${seconds % 60}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MidasGray,
                    )
                }
            }
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = call.status.uppercase(),
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Explicit visible CTA so tapping is unambiguous on iOS device,
            // where the wrapping Surface.onClick can be unreliable in some
            // Compose Multiplatform builds.
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Ver detalle",
                    tint = MidasGreen,
                )
            }
        }
    }
}

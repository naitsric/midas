package com.midas.ui.voip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.ui.theme.MidasDarkBg
import com.midas.ui.theme.MidasDarkCard
import com.midas.ui.theme.MidasGray
import com.midas.ui.theme.MidasGreen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.midas.voip.VoipCallManager
import com.midas.voip.VoipCallState
import com.midas.voip.VoipDebugLog

@Composable
fun VoipDialScreen(
    voipCallManager: VoipCallManager,
    onClose: () -> Unit,
) {
    var phoneNumber by remember { mutableStateOf("+57") }
    var clientName by remember { mutableStateOf("") }
    val activeCall by voipCallManager.activeCall.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidasDarkBg)
            .padding(24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Llamar (VoIP)",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(32.dp))

        if (activeCall != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        activeCall?.displayName ?: activeCall?.remoteNumber ?: "",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        activeCall?.remoteNumber ?: "",
                        color = MidasGray,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when (activeCall?.state) {
                            VoipCallState.CONNECTING -> "Conectando..."
                            VoipCallState.RINGING -> "Llamando..."
                            VoipCallState.CONNECTED -> "Conectado"
                            VoipCallState.ENDED -> "Finalizado"
                            VoipCallState.FAILED -> "Falló"
                            null -> ""
                        },
                        color = MidasGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { voipCallManager.hangup() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Colgar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            return@Column
        }

        // Dial form
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Número (E.164)", color = MidasGray) },
            placeholder = { Text("+573001234567", color = MidasGray.copy(alpha = 0.5f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MidasGreen,
                unfocusedBorderColor = MidasGray,
                cursorColor = MidasGreen,
            ),
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = clientName,
            onValueChange = { clientName = it },
            label = { Text("Nombre del cliente (opcional)", color = MidasGray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MidasGreen,
                unfocusedBorderColor = MidasGray,
                cursorColor = MidasGreen,
            ),
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                voipCallManager.dial(
                    toNumber = phoneNumber.trim(),
                    clientName = clientName.trim().ifBlank { null },
                )
            },
            enabled = phoneNumber.startsWith("+") && phoneNumber.length >= 8,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MidasGreen,
                contentColor = Color.Black,
                disabledContainerColor = MidasGreen.copy(alpha = 0.3f),
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Llamar", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))

        // In-app debug log (visible while the bridge is being debugged)
        val debugLines by VoipDebugLog.lines.collectAsState()
        if (debugLines.isNotEmpty()) {
            Text("Debug", color = MidasGray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    debugLines.forEach { line ->
                        Text(line, color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

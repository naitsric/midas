package com.midas.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.data.repository.SettingsRepository
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    apiClient: MidasApiClient,
    settings: SettingsRepository,
    onLoginSuccess: () -> Unit,
) {
    val s = LocalStrings.current
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidasDarkBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            // Logo "MIDAS"
            Row {
                Text(
                    text = "M",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MidasGreen,
                    letterSpacing = 8.sp,
                )
                Text(
                    text = "IDAS",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 8.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "CONVERSATION INTELLIGENCE",
                style = MaterialTheme.typography.labelSmall,
                color = MidasGray,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(48.dp))

            // Auth card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MidasDarkCardBorder.copy(alpha = 0.6f),
                                MidasDarkCardBorder.copy(alpha = 0.2f),
                            ),
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .background(
                        color = MidasDarkCard.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Section title
                    Text(
                        text = s.loginTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MidasGray,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(20.dp))

                    // API Key field with lock icon + paste action
                    TextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; error = null },
                        placeholder = {
                            Text(
                                s.loginApiKeyPlaceholder,
                                color = MidasGray.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MidasGray.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboard.getText()?.text?.let {
                                    apiKey = it.trim()
                                    error = null
                                }
                            }) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Pegar",
                                    tint = MidasGreen,
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error != null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MidasDarkBg.copy(alpha = 0.8f),
                            unfocusedContainerColor = MidasDarkBg.copy(alpha = 0.8f),
                            errorContainerColor = MidasDarkBg.copy(alpha = 0.8f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                            cursorColor = MidasGreen,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    // Hint text
                    Text(
                        text = if (error != null) error!! else s.loginApiKeyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) Color(0xFFEF5350) else MidasGray.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Start),
                    )

                    Spacer(Modifier.height(20.dp))

                    // Login button
                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                error = s.loginErrorEmpty
                                return@Button
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MidasGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = MidasGreen.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.4f),
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                s.loginButton,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Divider with "o"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MidasDarkCardBorder,
                        )
                        Text(
                            text = s.loginOr,
                            color = MidasGray.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MidasDarkCardBorder,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Register button
                    OutlinedButton(
                        onClick = { /* TODO: navigate to registration */ },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        border = BorderStroke(1.dp, MidasDarkCardBorder),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            s.loginRegister,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Support link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                Icon(
                    Icons.Default.HeadsetMic,
                    contentDescription = null,
                    tint = MidasGray.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = s.loginSupport,
                    style = MaterialTheme.typography.labelSmall,
                    color = MidasGray.copy(alpha = 0.4f),
                    letterSpacing = 1.5.sp,
                )
            }

            // Terms + Privacy
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(
                    text = s.loginTerms,
                    style = MaterialTheme.typography.labelSmall,
                    color = MidasGray.copy(alpha = 0.35f),
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = "  ·  ",
                    color = MidasGray.copy(alpha = 0.35f),
                    fontSize = 9.sp,
                )
                Text(
                    text = s.loginPrivacy,
                    style = MaterialTheme.typography.labelSmall,
                    color = MidasGray.copy(alpha = 0.35f),
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                )
            }

            // Version
            Text(
                text = "v1.0.0 — SECURE_SESSION_ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = MidasGray.copy(alpha = 0.25f),
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

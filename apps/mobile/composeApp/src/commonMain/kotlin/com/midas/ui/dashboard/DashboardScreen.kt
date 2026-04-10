package com.midas.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.Advisor
import com.midas.domain.model.CallSummary
import com.midas.domain.model.CreditApplication
import com.midas.ui.i18n.Language
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    apiClient: MidasApiClient,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
) {
    val s = LocalStrings.current
    var advisor by remember { mutableStateOf<Advisor?>(null) }
    var conversationCount by remember { mutableStateOf(0) }
    var applicationCount by remember { mutableStateOf(0) }
    var callCount by remember { mutableStateOf(0) }
    var recentApps by remember { mutableStateOf<List<CreditApplication>>(emptyList()) }
    var recentCalls by remember { mutableStateOf<List<CallSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                advisor = apiClient.getAdvisorProfile()
                val convos = apiClient.listConversations()
                val apps = apiClient.listApplications()
                val calls = apiClient.listCalls()
                conversationCount = convos.size
                applicationCount = apps.size
                callCount = calls.size
                recentApps = apps.take(3)
                recentCalls = calls.take(3)
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MidasGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (advisor?.name?.firstOrNull()?.toString() ?: "M").uppercase(),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "MIDAS",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Language toggle
                TextButton(
                    onClick = {
                        onLanguageChange(if (currentLanguage == Language.ES) Language.EN else Language.ES)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        if (currentLanguage == Language.ES) "EN" else "ES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MidasGray,
                    )
                }
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MidasGray,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Greeting
        Text(
            text = "${s.dashboardGreeting} ${advisor?.name ?: "..."}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = s.dashboardSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MidasGray,
        )

        Spacer(Modifier.height(24.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MidasGreen)
            }
        } else {
            // Stat cards
            StatCard(
                label = s.dashboardConversations.uppercase(),
                count = conversationCount,
                icon = Icons.Default.Chat,
            )
            Spacer(Modifier.height(12.dp))
            StatCard(
                label = s.dashboardCalls.uppercase(),
                count = callCount,
                icon = Icons.Default.Phone,
                accentColor = MidasPurple,
            )
            Spacer(Modifier.height(12.dp))
            StatCard(
                label = s.dashboardApplications.uppercase(),
                count = applicationCount,
                icon = Icons.Default.Description,
                accentColor = MidasOrange,
                badgeText = if (applicationCount > 0) "$applicationCount" else null,
                badgeColor = MidasOrange,
            )

            Spacer(Modifier.height(32.dp))

            // Recent activity header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (currentLanguage == Language.ES) "ACTIVIDAD RECIENTE" else "RECENT ACTIVITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MidasGray,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Recent activity items
            if (recentCalls.isEmpty() && recentApps.isEmpty()) {
                Text(
                    if (currentLanguage == Language.ES) "Sin actividad reciente" else "No recent activity",
                    color = MidasGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                recentCalls.forEach { call ->
                    ActivityItem(
                        icon = Icons.Default.Mic,
                        iconColor = MidasPurple,
                        title = call.clientName,
                        subtitle = call.durationSeconds?.let { "${it / 60}m ${it % 60}s" } ?: "",
                        statusText = call.status.uppercase(),
                        statusColor = when (call.status) {
                            "completed" -> MidasGreen
                            "recording" -> MidasOrange
                            else -> MidasPurple
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                recentApps.forEach { app ->
                    ActivityItem(
                        icon = Icons.Default.Description,
                        iconColor = MidasOrange,
                        title = app.applicant.fullName,
                        subtitle = app.productRequest.productLabel ?: app.productRequest.productType ?: "",
                        statusText = (app.statusLabel ?: app.status).uppercase(),
                        statusColor = when (app.status) {
                            "draft" -> MidasOrange
                            "submitted" -> MidasGreen
                            else -> MidasBlue
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color = MidasGreen,
    badgeText: String? = null,
    badgeColor: Color = MidasGreen,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = count.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (badgeText != null) {
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            Icon(
                icon,
                contentDescription = null,
                tint = MidasGray.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun ActivityItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(14.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MidasGray,
                    )
                }
            }

            // Status badge
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

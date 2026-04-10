package com.midas.ui.applications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.domain.model.CreditApplication
import com.midas.ui.i18n.LocalStrings
import com.midas.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ApplicationListScreen(apiClient: MidasApiClient) {
    var applications by remember { mutableStateOf<List<CreditApplication>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedApp by remember { mutableStateOf<CreditApplication?>(null) }
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                applications = apiClient.listApplications()
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    if (selectedApp != null) {
        ApplicationDetailScreen(app = selectedApp!!, onBack = { selectedApp = null })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MidasDarkBg)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                s.applicationsTitle,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MidasGreen)
                }
            } else if (applications.isEmpty()) {
                Text(s.applicationsEmpty, color = MidasGray, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(applications) { app ->
                        ApplicationCard(app, onClick = { selectedApp = app })
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationCard(app: CreditApplication, onClick: () -> Unit) {
    val statusColor = when (app.status) {
        "draft" -> MidasOrange
        "submitted" -> MidasGreen
        else -> MidasBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
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
                    .background(MidasOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MidasOrange,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.applicant.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                app.productRequest.productLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MidasGray)
                }
            }
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = (app.statusLabel ?: app.status).uppercase(),
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

@Composable
private fun ApplicationDetailScreen(app: CreditApplication, onBack: () -> Unit) {
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidasDarkBg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = s.back, tint = MidasGray)
            }
            Text(
                s.applicationDetail,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.applicationStatus, style = MaterialTheme.typography.labelSmall, color = MidasGray)
                StatusBadge(app.status, app.statusLabel)
            }

            Spacer(Modifier.height(16.dp))

            // Applicant section
            Text(
                s.applicationApplicant.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MidasGreen,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow(s.applicationName, app.applicant.fullName)
                    app.applicant.phone?.let { DetailRow(s.applicationPhone, it) }
                    app.applicant.estimatedIncome?.let { DetailRow(s.applicationIncome, it) }
                    app.applicant.employmentType?.let { DetailRow(s.applicationEmployment, it) }
                    app.applicant.completeness?.let {
                        DetailRow(s.applicationCompleteness, "${(it * 100).toInt()}%")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Product section
            Text(
                s.applicationProduct.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MidasGreen,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    app.productRequest.productLabel?.let { DetailRow(s.applicationProductType, it) }
                        ?: app.productRequest.productType?.let { DetailRow(s.applicationProductType, it) }
                    app.productRequest.amount?.let { DetailRow(s.applicationAmount, it) }
                    app.productRequest.term?.let { DetailRow(s.applicationTerm, it) }
                    app.productRequest.location?.let { DetailRow(s.applicationLocation, it) }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Summary
            Text(
                s.applicationSummary.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MidasGreen,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidasDarkCard),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    app.conversationSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                )
            }

            app.rejectionReason?.let { reason ->
                Spacer(Modifier.height(16.dp))
                Text(
                    s.applicationRejectionReason.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEF5350),
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1A1A)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MidasGray)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun StatusBadge(status: String, label: String? = null) {
    val color = when (status) {
        "draft" -> MidasOrange
        "submitted" -> MidasGreen
        "review" -> MidasBlue
        else -> MidasGray
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = (label ?: status.replaceFirstChar { it.uppercase() }).uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

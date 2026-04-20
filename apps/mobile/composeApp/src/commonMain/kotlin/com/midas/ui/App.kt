package com.midas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midas.data.api.MidasApiClient
import com.midas.data.defaultBaseUrl
import com.midas.data.repository.SettingsRepository
import com.midas.ui.applications.ApplicationListScreen
import com.midas.ui.auth.LoginScreen
import com.midas.ui.calls.CallDetailScreen
import com.midas.ui.calls.CallListScreen
import com.midas.ui.calls.RecordingScreen
import com.midas.ui.conversations.ConversationListScreen
import com.midas.ui.dashboard.DashboardScreen
import com.midas.ui.i18n.*
import com.midas.ui.voip.VoipDialScreen
import com.midas.voip.VoipCallManager
import com.midas.ui.theme.MidasDarkBg
import com.midas.ui.theme.MidasDarkCard
import com.midas.ui.theme.MidasGreen
import com.midas.ui.theme.MidasGray
import com.midas.ui.theme.MidasTheme

enum class Screen {
    Dashboard, Conversations, Applications, Calls, NewRecording, VoipDial, CallDetail
}

@Composable
fun MidasApp(
    settings: SettingsRepository = remember { SettingsRepository() },
    apiClient: MidasApiClient = remember {
        MidasApiClient(
            baseUrl = defaultBaseUrl,
            apiKeyProvider = { settings.getApiKey() },
        )
    },
    voipCallManager: VoipCallManager? = null,
) {
    val storedApiKey by settings.apiKey.collectAsState()
    var isLoggedIn by remember { mutableStateOf(storedApiKey != null) }
    var currentLanguage by remember { mutableStateOf(Language.ES) }
    val strings = remember(currentLanguage) { stringsFor(currentLanguage) }

    CompositionLocalProvider(LocalStrings provides strings) {
        MidasTheme {
            if (!isLoggedIn) {
                LoginScreen(
                    apiClient = apiClient,
                    settings = settings,
                    onLoginSuccess = { isLoggedIn = true },
                )
            } else {
                MainScaffold(
                    apiClient = apiClient,
                    settings = settings,
                    voipCallManager = voipCallManager,
                    currentLanguage = currentLanguage,
                    onLanguageChange = {
                        currentLanguage = it
                        settings.saveLanguage(it.code)
                    },
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    apiClient: MidasApiClient,
    settings: SettingsRepository,
    voipCallManager: VoipCallManager?,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
) {
    val s = LocalStrings.current
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var selectedCallId by remember { mutableStateOf<String?>(null) }
    val showBottomBar = currentScreen != Screen.NewRecording &&
        currentScreen != Screen.VoipDial &&
        currentScreen != Screen.CallDetail

    data class NavItem(val screen: Screen, val label: String, val icon: @Composable () -> Unit)

    val navItems = listOf(
        NavItem(Screen.Dashboard, s.navHome) { Icon(Icons.Default.Home, contentDescription = s.navHome) },
        NavItem(Screen.Conversations, s.navChats) { Icon(Icons.Default.ChatBubble, contentDescription = s.navChats) },
        NavItem(Screen.Applications, s.navApplications) { Icon(Icons.Default.Description, contentDescription = s.navApplications) },
        NavItem(Screen.Calls, s.navCalls) { Icon(Icons.Default.Phone, contentDescription = s.navCalls) },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MidasDarkBg,
            bottomBar = {
                if (showBottomBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MidasDarkBg)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        navItems.forEach { item ->
                            val selected = currentScreen == item.screen
                            val color = if (selected) MidasGreen else MidasGray
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { currentScreen = item.screen }
                                    .padding(vertical = 4.dp),
                            ) {
                                CompositionLocalProvider(LocalContentColor provides color) {
                                    item.icon()
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.label,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            },
        ) { paddingValues ->
            Surface(
                modifier = Modifier.padding(paddingValues),
                color = MidasDarkBg,
            ) {
                when (currentScreen) {
                    Screen.Dashboard -> DashboardScreen(
                        apiClient = apiClient,
                        currentLanguage = currentLanguage,
                        onLanguageChange = onLanguageChange,
                    )
                    Screen.Conversations -> ConversationListScreen(apiClient)
                    Screen.Applications -> ApplicationListScreen(apiClient)
                    Screen.Calls -> CallListScreen(
                        apiClient = apiClient,
                        onNewRecording = { currentScreen = Screen.NewRecording },
                        onCallClick = { id ->
                            println("[App] navigating to CallDetail id=$id")
                            selectedCallId = id
                            currentScreen = Screen.CallDetail
                        },
                    )
                    Screen.CallDetail -> {
                        val id = selectedCallId
                        if (id != null) {
                            CallDetailScreen(
                                apiClient = apiClient,
                                callId = id,
                                onBack = { currentScreen = Screen.Calls },
                            )
                        } else {
                            currentScreen = Screen.Calls
                        }
                    }
                    Screen.NewRecording -> RecordingScreen(
                        apiClient = apiClient,
                        settings = settings,
                        onFinished = { currentScreen = Screen.Calls },
                    )
                    Screen.VoipDial -> {
                        if (voipCallManager != null) {
                            VoipDialScreen(
                                voipCallManager = voipCallManager,
                                onClose = { currentScreen = Screen.Dashboard },
                            )
                        } else {
                            currentScreen = Screen.Dashboard
                        }
                    }
                }
            }
        }

        // Floating action buttons overlay — hover above content + bottom nav
        if (showBottomBar) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (voipCallManager != null) {
                    FloatingActionButton(
                        onClick = { currentScreen = Screen.VoipDial },
                        containerColor = MidasGreen,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Llamar")
                    }
                }
                FloatingActionButton(
                    onClick = { currentScreen = Screen.NewRecording },
                    containerColor = MidasGreen,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = s.callNewRecording)
                }
            }
        }
    }
}

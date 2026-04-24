package com.midas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.midas.ui.conversations.ConversationDetailScreen
import com.midas.ui.conversations.ConversationListScreen
import com.midas.ui.copilot.CopilotScreen
import com.midas.ui.dashboard.DashboardScreen
import com.midas.ui.i18n.*
import com.midas.ui.onboarding.OnboardingScreen
import com.midas.ui.profile.ProfileScreen
import com.midas.ui.voip.VoipDialScreen
import com.midas.calendar.CalendarBridge
import com.midas.voip.VoipCallManager
import com.midas.ui.theme.LocalMidasColors
import com.midas.ui.theme.MidasTheme
import kotlinx.coroutines.launch

enum class Screen {
    Dashboard, Conversations, ConversationDetail, Applications, Calls, Copilot, NewRecording, VoipDial, CallDetail, Profile
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
    calendarBridge: CalendarBridge? = null,
) {
    val storedApiKey by settings.apiKey.collectAsState()
    val onboarded by settings.onboarded.collectAsState()
    var isLoggedIn by remember { mutableStateOf(storedApiKey != null) }
    var currentLanguage by remember { mutableStateOf(Language.EN) }
    val strings = remember(currentLanguage) { stringsFor(currentLanguage) }
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalStrings provides strings) {
        MidasTheme {
            when {
                !isLoggedIn -> LoginScreen(
                    apiClient = apiClient,
                    settings = settings,
                    onLoginSuccess = { isLoggedIn = true },
                )
                !onboarded -> OnboardingScreen(
                    onDone = { settings.markOnboarded() },
                )
                else -> MainScaffold(
                    apiClient = apiClient,
                    settings = settings,
                    voipCallManager = voipCallManager,
                    calendarBridge = calendarBridge,
                    currentLanguage = currentLanguage,
                    onLanguageChange = {
                        currentLanguage = it
                        settings.saveLanguage(it.code)
                    },
                    onLogout = {
                        scope.launch {
                            settings.clearApiKey()
                            isLoggedIn = false
                        }
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
    calendarBridge: CalendarBridge?,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    onLogout: () -> Unit,
) {
    val s = LocalStrings.current
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var selectedCallId by remember { mutableStateOf<String?>(null) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    val showBottomBar = currentScreen != Screen.NewRecording &&
        currentScreen != Screen.VoipDial &&
        currentScreen != Screen.CallDetail &&
        currentScreen != Screen.ConversationDetail &&
        currentScreen != Screen.Profile

    data class NavItem(val screen: Screen, val label: String, val icon: @Composable () -> Unit)

    val navItems = listOf(
        NavItem(Screen.Dashboard, s.navHome) { Icon(Icons.Default.Home, contentDescription = s.navHome) },
        NavItem(Screen.Conversations, s.navChats) { Icon(Icons.Default.ChatBubble, contentDescription = s.navChats) },
        NavItem(Screen.Copilot, s.navCopilot) { Icon(Icons.Default.AutoAwesome, contentDescription = s.navCopilot) },
        NavItem(Screen.Applications, s.navApplications) { Icon(Icons.Default.Description, contentDescription = s.navApplications) },
        NavItem(Screen.Calls, s.navCalls) { Icon(Icons.Default.Phone, contentDescription = s.navCalls) },
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = backgroundColor,
            bottomBar = {
                if (showBottomBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val midasColors = LocalMidasColors.current
                        navItems.forEach { item ->
                            val selected = currentScreen == item.screen
                            val color = if (selected) midasColors.primaryAccent else midasColors.muted
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
                color = backgroundColor,
            ) {
                when (currentScreen) {
                    Screen.Dashboard -> DashboardScreen(
                        apiClient = apiClient,
                        currentLanguage = currentLanguage,
                        onLanguageChange = onLanguageChange,
                        onLogout = onLogout,
                        onRecord = { currentScreen = Screen.NewRecording },
                        onCall = {
                            if (voipCallManager != null) currentScreen = Screen.VoipDial
                        },
                        onProfile = { currentScreen = Screen.Profile },
                    )
                    Screen.Profile -> {
                        val storedKey by settings.apiKey.collectAsState()
                        ProfileScreen(
                            apiClient = apiClient,
                            currentLanguage = currentLanguage,
                            onLanguageChange = onLanguageChange,
                            apiKey = storedKey,
                            voipConnected = voipCallManager != null,
                            onLogout = onLogout,
                            onBack = { currentScreen = Screen.Dashboard },
                        )
                    }
                    Screen.Conversations -> ConversationListScreen(
                        apiClient = apiClient,
                        onConversationClick = { id ->
                            selectedConversationId = id
                            currentScreen = Screen.ConversationDetail
                        },
                    )
                    Screen.ConversationDetail -> {
                        val id = selectedConversationId
                        if (id != null) {
                            ConversationDetailScreen(
                                apiClient = apiClient,
                                conversationId = id,
                                onBack = { currentScreen = Screen.Conversations },
                            )
                        } else {
                            currentScreen = Screen.Conversations
                        }
                    }
                    Screen.Applications -> ApplicationListScreen(apiClient)
                    Screen.Copilot -> CopilotScreen(apiClient = apiClient, calendarBridge = calendarBridge)
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
                                apiClient = apiClient,
                                onClose = { currentScreen = Screen.Dashboard },
                                onOpenCallDetail = { id ->
                                    selectedCallId = id
                                    currentScreen = Screen.CallDetail
                                },
                            )
                        } else {
                            currentScreen = Screen.Dashboard
                        }
                    }
                }
            }
        }

        // Floating action buttons overlay — hover above content + bottom nav.
        // Hidden on Copilot to avoid overlapping the composer input.
        if (showBottomBar && currentScreen != Screen.Copilot) {
            val midasColors = LocalMidasColors.current
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
                        containerColor = midasColors.primaryAccent,
                        contentColor = midasColors.primaryAccentOn,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "Llamar")
                    }
                }
                FloatingActionButton(
                    onClick = { currentScreen = Screen.NewRecording },
                    containerColor = midasColors.primaryAccent,
                    contentColor = midasColors.primaryAccentOn,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = s.callNewRecording)
                }
            }
        }
    }
}

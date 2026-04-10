package com.midas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.midas.ui.calls.CallListScreen
import com.midas.ui.calls.RecordingScreen
import com.midas.ui.conversations.ConversationListScreen
import com.midas.ui.dashboard.DashboardScreen
import com.midas.ui.i18n.*
import com.midas.ui.theme.MidasDarkBg
import com.midas.ui.theme.MidasDarkCard
import com.midas.ui.theme.MidasGreen
import com.midas.ui.theme.MidasGray
import com.midas.ui.theme.MidasTheme

enum class Screen {
    Dashboard, Conversations, Applications, Calls, NewRecording
}

@Composable
fun MidasApp() {
    val settings = remember { SettingsRepository() }
    val apiClient = remember {
        MidasApiClient(
            baseUrl = defaultBaseUrl,
            apiKeyProvider = { settings.getApiKey() },
        )
    }

    var isLoggedIn by remember { mutableStateOf(false) }
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
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
) {
    val s = LocalStrings.current
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    val showBottomBar = currentScreen != Screen.NewRecording

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
                    Column {
                        // Floating "Grabar llamada" pill
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FloatingActionButton(
                                onClick = { currentScreen = Screen.NewRecording },
                                containerColor = MidasGreen,
                                contentColor = Color.Black,
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Text(
                                        s.callNewRecording,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                        // Bottom navigation
                        NavigationBar(
                            containerColor = MidasDarkBg,
                            tonalElevation = 0.dp,
                        ) {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    icon = item.icon,
                                    label = {
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                        )
                                    },
                                    selected = currentScreen == item.screen,
                                    onClick = { currentScreen = item.screen },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MidasGreen,
                                        selectedTextColor = MidasGreen,
                                        unselectedIconColor = MidasGray,
                                        unselectedTextColor = MidasGray,
                                        indicatorColor = MidasGreen.copy(alpha = 0.12f),
                                    ),
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
                    )
                    Screen.NewRecording -> RecordingScreen(
                        apiClient = apiClient,
                        settings = settings,
                        onFinished = { currentScreen = Screen.Calls },
                    )
                }
            }
        }
    }
}

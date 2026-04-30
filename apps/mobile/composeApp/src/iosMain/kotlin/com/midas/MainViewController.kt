package com.midas

import androidx.compose.ui.window.ComposeUIViewController
import com.midas.ui.MidasApp

fun MainViewController() = ComposeUIViewController(
    configure = {
        enforceStrictPlistSanityCheck = false
    }
) {
    MidasApp(
        settings = MidasContext.settings,
        apiClient = MidasContext.apiClient,
        voipCallManager = MidasContext.voipCallManager,
        calendarBridge = MidasContext.calendarBridge,
        audioPlayerBridge = MidasContext.audioPlayerBridge,
    )
}

package com.midas.ui.permissions

import androidx.compose.runtime.Composable

actual class AudioPermissionState {
    actual val granted: Boolean = true
    actual fun request() {}
}

@Composable
actual fun rememberAudioPermissionState(): AudioPermissionState = AudioPermissionState()

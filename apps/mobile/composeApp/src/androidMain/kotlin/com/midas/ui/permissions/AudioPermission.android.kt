package com.midas.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

actual class AudioPermissionState(
    private val _granted: MutableState<Boolean>,
    private val activity: Activity?,
) {
    actual val granted: Boolean get() = _granted.value
    actual fun request() {
        activity?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        // After returning, recheck
        activity?.let {
            _granted.value = it.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
actual fun rememberAudioPermissionState(): AudioPermissionState {
    val context = LocalContext.current
    val activity = context as? Activity
    val granted = remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // Recheck on every recomposition (covers returning from permission dialog)
    LaunchedEffect(Unit) {
        granted.value = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    return remember(granted.value) { AudioPermissionState(granted, activity) }
}

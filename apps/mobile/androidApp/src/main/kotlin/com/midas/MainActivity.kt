package com.midas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.midas.audioplayer.AudioPlayerBridgeImpl
import com.midas.data.storage.AndroidContextHolder
import com.midas.ui.MidasApp

class MainActivity : ComponentActivity() {
    private val audioPlayerBridge by lazy { AudioPlayerBridgeImpl() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // KeyValueStorage (SharedPreferences-based) reads from AndroidContextHolder
        // on construction. Compose's MidasApp() instantiates SettingsRepository
        // eagerly via remember{}, so the holder must be primed before setContent.
        AndroidContextHolder.context = applicationContext
        setContent {
            MidasApp(
                audioPlayerBridge = audioPlayerBridge,
            )
        }
    }

    override fun onDestroy() {
        audioPlayerBridge.release()
        super.onDestroy()
    }
}

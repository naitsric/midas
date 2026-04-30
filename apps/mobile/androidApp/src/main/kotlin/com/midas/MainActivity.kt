package com.midas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.midas.audioplayer.AudioPlayerBridgeImpl
import com.midas.data.repository.SettingsRepository
import com.midas.data.storage.AndroidContextHolder
import com.midas.data.storage.KeyValueStorage
import com.midas.ui.MidasApp
import com.midas.voip.AndroidVoipDispatcher
import com.midas.voip.CallControlClient
import com.midas.voip.defaultCallControlBaseUrl

class MainActivity : ComponentActivity() {

    private val audioPlayerBridge by lazy { AudioPlayerBridgeImpl() }

    private val settings by lazy { SettingsRepository(KeyValueStorage()) }

    private val callControlClient by lazy {
        CallControlClient(
            baseUrl = defaultCallControlBaseUrl,
            apiKeyProvider = { settings.getApiKey() },
        )
    }

    private val voipDispatcher by lazy {
        AndroidVoipDispatcher(
            appContext = applicationContext,
            callControlClient = callControlClient,
        )
    }

    private val voipCallManager by lazy {
        createVoipCallManager(voipDispatcher).also { manager ->
            voipDispatcher.attach(manager)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* If denied, dial() will fail gracefully and surface the failure to the UI. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // KeyValueStorage (SharedPreferences-based) reads from AndroidContextHolder
        // on construction. Compose's MidasApp() instantiates SettingsRepository
        // eagerly via remember{}, so the holder must be primed before setContent.
        AndroidContextHolder.context = applicationContext

        // Pedir RECORD_AUDIO proactivamente al primer launch — coincide con el
        // patrón iOS (iOSApp.swift hace lo mismo en didFinishLaunching). El
        // AndroidVoipDispatcher.dial() chequea de nuevo y aborta si fue denegado.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MidasApp(
                voipCallManager = voipCallManager,
                audioPlayerBridge = audioPlayerBridge,
            )
        }
    }

    override fun onDestroy() {
        audioPlayerBridge.release()
        voipDispatcher.release()
        super.onDestroy()
    }
}

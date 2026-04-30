package com.midas

import com.midas.audioplayer.AudioPlayerBridge
import com.midas.calendar.CalendarBridge
import com.midas.data.api.MidasApiClient
import com.midas.data.defaultBaseUrl
import com.midas.data.repository.SettingsRepository
import com.midas.data.storage.KeyValueStorage
import com.midas.voip.VoipCallManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton accesible desde Swift para que el AppDelegate (iOS native) pueda
 * leer la API key persistida y reaccionar a cambios cuando el asesor hace login/logout.
 *
 * Uso desde Swift:
 *   let key = MidasContextKt.MidasContext.shared.currentApiKey()
 *   MidasContextKt.MidasContext.shared.observeApiKey { key in ... }
 */
object MidasContext {
    val settings: SettingsRepository = SettingsRepository(KeyValueStorage())

    val apiClient: MidasApiClient = MidasApiClient(
        baseUrl = defaultBaseUrl,
        apiKeyProvider = { settings.getApiKey() },
    )

    val baseUrl: String get() = defaultBaseUrl

    /** Settable desde Swift (AppDelegate) tras inicializar CallKit + dispatcher. */
    var voipCallManager: VoipCallManager? = null

    /** Settable desde Swift (AppDelegate). Implementación nativa con EventKit. */
    var calendarBridge: CalendarBridge? = null

    /** Settable desde Swift (AppDelegate). Implementación nativa con AVPlayer. */
    var audioPlayerBridge: AudioPlayerBridge? = null

    fun currentApiKey(): String? = settings.apiKey.value

    fun apiKeyFlow(): StateFlow<String?> = settings.apiKey
}

package com.midas

import com.midas.voip.VoipCallDispatcher
import com.midas.voip.VoipCallManager

/**
 * Equivalente Android del `composeApp/iosMain/com/midas/VoipCallFactory.kt`.
 *
 * Construye el `VoipCallManager` con el dispatcher Android (Chime SDK +
 * MediaPlayer ringback + ForegroundService). Tras invocar esta factoría,
 * el caller debe hacer `dispatcher.attach(manager)` para resolver el cycle
 * (manager y dispatcher se referencian mutuamente).
 */
fun createVoipCallManager(dispatcher: VoipCallDispatcher): VoipCallManager =
    VoipCallManager(dispatcher)

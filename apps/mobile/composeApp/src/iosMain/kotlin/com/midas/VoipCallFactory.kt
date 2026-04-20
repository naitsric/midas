package com.midas

import com.midas.voip.VoipCallDispatcher
import com.midas.voip.VoipCallManager

fun createVoipCallManager(dispatcher: VoipCallDispatcher): VoipCallManager =
    VoipCallManager(dispatcher)

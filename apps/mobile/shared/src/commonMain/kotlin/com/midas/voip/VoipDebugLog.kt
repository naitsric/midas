package com.midas.voip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-app debug log — mostly for iOS, where viewing NSLog on device is painful.
 * Call append() from any side of the bridge (Kotlin or Swift), and the
 * VoipDialScreen will render the last N entries live.
 */
object VoipDebugLog {
    private const val MAX_ENTRIES = 100

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun append(line: String) {
        val next = (_lines.value + line).takeLast(MAX_ENTRIES)
        _lines.value = next
    }

    fun clear() {
        _lines.value = emptyList()
    }
}

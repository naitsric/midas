package com.midas.data.storage

import android.content.Context

actual class KeyValueStorage actual constructor() {
    private val prefs = AndroidContextHolder.context.getSharedPreferences(
        "midas_settings",
        Context.MODE_PRIVATE,
    )

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

object AndroidContextHolder {
    lateinit var context: Context
}

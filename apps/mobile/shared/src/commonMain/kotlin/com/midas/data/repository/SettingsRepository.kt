package com.midas.data.repository

import com.midas.data.storage.KeyValueStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val KEY_API_KEY = "api_key"
private const val KEY_LANGUAGE = "language"

class SettingsRepository(
    private val storage: KeyValueStorage = KeyValueStorage(),
) {
    private val _apiKey = MutableStateFlow(storage.getString(KEY_API_KEY))
    private val _language = MutableStateFlow(storage.getString(KEY_LANGUAGE) ?: "es")

    val apiKey: StateFlow<String?> = _apiKey
    val language: StateFlow<String> = _language

    suspend fun saveApiKey(key: String) {
        storage.putString(KEY_API_KEY, key)
        _apiKey.value = key
    }

    suspend fun clearApiKey() {
        storage.remove(KEY_API_KEY)
        _apiKey.value = null
    }

    suspend fun getApiKey(): String? = _apiKey.value

    fun saveLanguage(code: String) {
        storage.putString(KEY_LANGUAGE, code)
        _language.value = code
    }

    fun getLanguage(): String = _language.value
}

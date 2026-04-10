package com.midas.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository {
    private val _apiKey = MutableStateFlow<String?>(null)
    private val _apiUrl = MutableStateFlow("http://localhost:8005")
    private val _language = MutableStateFlow("es")

    val apiKey: StateFlow<String?> = _apiKey
    val apiUrl: StateFlow<String> = _apiUrl
    val language: StateFlow<String> = _language

    suspend fun saveApiKey(key: String) {
        _apiKey.value = key
    }

    suspend fun clearApiKey() {
        _apiKey.value = null
    }

    suspend fun getApiKey(): String? = _apiKey.value

    suspend fun saveApiUrl(url: String) {
        _apiUrl.value = url
    }

    suspend fun getApiUrl(): String = _apiUrl.value

    fun saveLanguage(code: String) {
        _language.value = code
    }

    fun getLanguage(): String = _language.value
}

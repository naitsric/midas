package com.midas.data.repository

import com.midas.data.api.MidasApiClient
import com.midas.domain.model.Advisor

class AuthRepository(
    private val api: MidasApiClient,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun login(email: String): Result<Advisor> {
        return try {
            // Intentar obtener el perfil con la API key guardada
            val advisor = api.getAdvisorProfile()
            Result.success(advisor)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateApiKey(apiKey: String): Result<Advisor> {
        return try {
            settingsRepository.saveApiKey(apiKey)
            val advisor = api.getAdvisorProfile()
            Result.success(advisor)
        } catch (e: Exception) {
            settingsRepository.clearApiKey()
            Result.failure(e)
        }
    }

    suspend fun logout() {
        settingsRepository.clearApiKey()
    }

    suspend fun isLoggedIn(): Boolean {
        return settingsRepository.getApiKey() != null
    }
}

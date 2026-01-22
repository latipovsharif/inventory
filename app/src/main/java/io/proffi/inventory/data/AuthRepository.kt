package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.LoginRequest
import io.proffi.inventory.network.LoginResponse
import io.proffi.inventory.network.RefreshTokenRequest
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            tokenManager.saveTokens(response.access_token, response.refresh_token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<LoginResponse> {
        return try {
            val refreshToken = tokenManager.getRefreshToken().first()
                ?: return Result.failure(Exception("No refresh token available"))

            val response = apiService.refreshToken(RefreshTokenRequest(refreshToken))
            tokenManager.saveTokens(response.access_token, response.refresh_token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenManager.clearTokens()
    }

    suspend fun isAuthenticated(): Boolean {
        val token = tokenManager.getAccessToken().first()
        return token != null
    }
}


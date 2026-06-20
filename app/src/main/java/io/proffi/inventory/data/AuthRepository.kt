package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.LoginRequest
import io.proffi.inventory.network.LoginResponse
import io.proffi.inventory.network.RefreshTokenRequest
import io.proffi.inventory.network.safeApiCall
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Result<LoginResponse> {
        val result = safeApiCall { apiService.login(LoginRequest(email, password)) }
        result.getOrNull()?.let {
            tokenManager.saveTokens(it.accessToken, it.refreshToken, it.expiresIn)
        }
        return result
    }

    suspend fun refreshToken(): Result<LoginResponse> {
        val refreshToken = tokenManager.getRefreshToken().first()
            ?: return Result.failure(Exception("No refresh token available"))

        val result = safeApiCall { apiService.refreshToken(RefreshTokenRequest(refreshToken)) }
        result.getOrNull()?.let {
            tokenManager.saveTokens(it.accessToken, it.refreshToken, it.expiresIn)
        }
        return result
    }

    suspend fun logout() {
        tokenManager.clearTokens()
    }

    suspend fun isAuthenticated(): Boolean {
        val token = tokenManager.getAccessToken().first()
        return token != null
    }
}

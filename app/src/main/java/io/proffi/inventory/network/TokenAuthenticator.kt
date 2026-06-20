package io.proffi.inventory.network

import io.proffi.inventory.data.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reactively refreshes the access token when the server returns 401.
 * On refresh failure the stored tokens are cleared so the app falls back to login.
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val refreshApi: RefreshApiService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Stop after one retry to avoid an infinite 401 loop.
        if (responseCount(response) >= 2) return null

        synchronized(this) {
            val failedToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.trim()

            // Another request may have already refreshed — reuse the fresh token.
            val current = tokenManager.peekAccessToken()
            if (current != null && current != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $current")
                    .build()
            }

            val refreshToken = tokenManager.peekRefreshToken()
                ?: runBlocking {
                    tokenManager.ensureLoaded()
                    tokenManager.peekRefreshToken()
                }
                ?: return null

            val newTokens = runBlocking {
                try {
                    refreshApi.api.refreshToken(RefreshTokenRequest(refreshToken))
                } catch (e: Exception) {
                    null
                }
            }

            if (newTokens == null) {
                // Refresh failed (expired/invalid refresh token) — force re-login.
                runBlocking { tokenManager.clearTokens() }
                return null
            }

            runBlocking {
                tokenManager.saveTokens(
                    newTokens.accessToken,
                    newTokens.refreshToken,
                    newTokens.expiresIn
                )
            }

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

package io.proffi.inventory.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import io.proffi.inventory.data.TokenManager

class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // Prefer the in-memory cache; fall back to a one-time disk prime if empty.
        val token = tokenManager.peekAccessToken() ?: runBlocking {
            tokenManager.ensureLoaded()
            tokenManager.peekAccessToken()
        }

        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}

package io.proffi.inventory.network

import io.proffi.inventory.BuildConfig
import io.proffi.inventory.util.AppConfig
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private fun loggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // Only log request/response bodies in debug builds — BODY level leaks
            // credentials and Bearer tokens to logcat, so disable it in release.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    private fun baseClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .connectTimeout(AppConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(AppConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)

    private fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()

    fun create(
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator
    ): ApiService {
        val client = baseClientBuilder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .build()
        return retrofit(client).create(ApiService::class.java)
    }

    /**
     * Bare client for the token-refresh call only: no auth interceptor and no
     * authenticator, so a 401 from the refresh endpoint cannot loop.
     */
    fun createAuthApi(): ApiService {
        val client = baseClientBuilder().build()
        return retrofit(client).create(ApiService::class.java)
    }
}

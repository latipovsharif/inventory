package io.proffi.inventory.data


import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT_KEY = longPreferencesKey("expires_at")
    }

    // In-memory cache so the OkHttp interceptor/authenticator can read the token
    // synchronously without a DataStore round-trip on every request.
    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var cachedRefreshToken: String? = null
    @Volatile private var cacheLoaded = false

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Int? = null
    ) {
        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken
        cacheLoaded = true
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            if (expiresInSeconds != null) {
                preferences[EXPIRES_AT_KEY] =
                    System.currentTimeMillis() + expiresInSeconds * 1000L
            }
        }
    }

    fun getAccessToken(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }

    fun getRefreshToken(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN_KEY]
    }

    /** Synchronous cached access token for the OkHttp layer. Null until primed/saved. */
    fun peekAccessToken(): String? = cachedAccessToken

    /** Synchronous cached refresh token for the OkHttp layer. Null until primed/saved. */
    fun peekRefreshToken(): String? = cachedRefreshToken

    /** Prime the in-memory cache from disk once (no-op if already loaded). */
    suspend fun ensureLoaded() {
        if (!cacheLoaded) {
            val preferences = context.dataStore.data.first()
            cachedAccessToken = preferences[ACCESS_TOKEN_KEY]
            cachedRefreshToken = preferences[REFRESH_TOKEN_KEY]
            cacheLoaded = true
        }
    }

    suspend fun clearTokens() {
        cachedAccessToken = null
        cachedRefreshToken = null
        cacheLoaded = true
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(EXPIRES_AT_KEY)
        }
    }
}

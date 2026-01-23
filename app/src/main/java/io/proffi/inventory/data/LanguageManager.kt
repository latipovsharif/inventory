package io.proffi.inventory.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class LanguageManager(private val context: Context) {

    companion object {
        const val LANGUAGE_RUSSIAN = "ru"
        const val LANGUAGE_ENGLISH = "en"
    }

    private val _currentLanguage = MutableStateFlow<String?>(null)
    val currentLanguage: StateFlow<String?> = _currentLanguage.asStateFlow()

    suspend fun saveLanguage(languageCode: String) {
        context.languageDataStore.edit { preferences ->
            preferences[DataStoreKeys.SELECTED_LANGUAGE] = languageCode
        }
        _currentLanguage.value = languageCode
    }

    fun getLanguage(): Flow<String?> = context.languageDataStore.data.map { preferences ->
        preferences[DataStoreKeys.SELECTED_LANGUAGE]
    }

    suspend fun clearLanguage() {
        context.languageDataStore.edit { preferences ->
            preferences.remove(DataStoreKeys.SELECTED_LANGUAGE)
        }
        _currentLanguage.value = null
    }

    fun setCurrentLanguage(languageCode: String?) {
        _currentLanguage.value = languageCode
    }
}

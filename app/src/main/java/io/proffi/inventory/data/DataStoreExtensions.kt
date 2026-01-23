package io.proffi.inventory.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Singleton extension для DataStore
 * ВАЖНО: Определяется только ОДИН раз на уровне приложения
 */
val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_prefs")

/**
 * Ключи для DataStore
 */
object DataStoreKeys {
    val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
}

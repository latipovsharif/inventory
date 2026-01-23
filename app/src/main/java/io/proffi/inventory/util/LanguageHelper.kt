package io.proffi.inventory.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.proffi.inventory.data.DataStoreKeys
import io.proffi.inventory.data.languageDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Утилита для управления языком приложения
 * Использует AndroidX AppCompatDelegate для автоматического управления локалью
 */
object LanguageHelper {

    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SYSTEM = "system"

    /**
     * Инициализация языка при старте приложения
     * Вызывается в Application.onCreate()
     */
    suspend fun initialize(context: Context) {
        val savedLanguage = getSavedLanguage(context)
        applyLanguage(savedLanguage)
    }

    /**
     * Применить язык к приложению
     */
    fun applyLanguage(languageCode: String) {
        val localeList = when (languageCode) {
            LANGUAGE_SYSTEM -> LocaleListCompat.getEmptyLocaleList() // Системный язык
            else -> LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    /**
     * Сохранить выбранный язык в DataStore
     */
    suspend fun saveLanguage(context: Context, languageCode: String) {
        context.languageDataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                set(DataStoreKeys.SELECTED_LANGUAGE, languageCode)
            }
        }
    }

    /**
     * Получить сохранённый язык из DataStore
     */
    suspend fun getSavedLanguage(context: Context): String {
        return context.languageDataStore.data.map { preferences ->
            preferences[DataStoreKeys.SELECTED_LANGUAGE] ?: LANGUAGE_SYSTEM
        }.first()
    }

    /**
     * Получить текущий активный язык
     */
    fun getCurrentLanguage(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) {
            LANGUAGE_SYSTEM
        } else {
            locales[0]?.language ?: LANGUAGE_SYSTEM
        }
    }

    /**
     * Получить отображаемое имя языка
     */
    fun getLanguageDisplayName(languageCode: String, context: Context): String {
        return when (languageCode) {
            LANGUAGE_RUSSIAN -> "Русский"
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_SYSTEM -> {
                val systemLocale = Locale.getDefault()
                when (systemLocale.language) {
                    "ru" -> "Русский (системный)"
                    else -> "English (system)"
                }
            }
            else -> languageCode
        }
    }
}

package io.proffi.inventory.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    /**
     * Обновляет локаль приложения
     */
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        configuration.setLocale(locale)

        return context.createConfigurationContext(configuration)
    }

    /**
     * Получает текущую локаль
     */
    fun getCurrentLocale(context: Context): Locale {
        return context.resources.configuration.locales[0]
    }

    /**
     * Получает код языка (например, "ru" или "en")
     */
    fun getCurrentLanguageCode(context: Context): String {
        return getCurrentLocale(context).language
    }
}

package io.proffi.inventory.ui.base

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/**
 * Глобальный singleton для управления версией языка
 * Используется для синхронизации обновлений UI между всеми Activity
 */
object LanguageState {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /**
     * Mutex для предотвращения race condition при смене языка
     */
    val mutex = Mutex()

    /**
     * Текущий применённый язык (для проверки в onResume)
     */
    var currentAppliedLanguage: String? = null

    fun bumpVersion() {
        _version.update { it + 1 }
    }
}

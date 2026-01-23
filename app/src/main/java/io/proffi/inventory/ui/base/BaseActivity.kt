package io.proffi.inventory.ui.base

import androidx.appcompat.app.AppCompatActivity

/**
 * Базовый Activity для приложения
 * Использует AppCompatActivity для автоматической поддержки мультиязычности через AndroidX
 */
open class BaseActivity : AppCompatActivity() {
    // Всё управление языком происходит автоматически через AppCompatDelegate
    // Не требуется ручная работа с конфигурацией или перерисовкой UI
}

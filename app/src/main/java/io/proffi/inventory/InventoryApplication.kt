package io.proffi.inventory

import android.app.Application
import io.proffi.inventory.di.appModule
import io.proffi.inventory.util.LanguageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class InventoryApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Инициализация Koin
        startKoin {
            androidLogger()
            androidContext(this@InventoryApplication)
            modules(appModule)
        }

        // Инициализация языка (асинхронно)
        applicationScope.launch {
            LanguageHelper.initialize(this@InventoryApplication)
        }
    }
}

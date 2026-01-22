package io.proffi.inventory

import android.app.Application
import io.proffi.inventory.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class InventoryApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@InventoryApplication)
            modules(appModule)
        }
    }
}

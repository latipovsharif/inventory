package io.proffi.inventory.di

import io.proffi.inventory.data.*
import io.proffi.inventory.network.AuthInterceptor
import io.proffi.inventory.network.RetrofitClient
import io.proffi.inventory.ui.inventory.InventoryViewModel
import io.proffi.inventory.ui.login.LoginViewModel
import io.proffi.inventory.ui.scanner.ScannerViewModel
import io.proffi.inventory.ui.warehouse.WarehouseViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // TokenManager
    single { TokenManager(androidContext()) }

    // LanguageManager
    single { LanguageManager(androidContext()) }

    // Network
    single { AuthInterceptor(get()) }
    single { RetrofitClient.create(get()) }

    // Repositories
    single { AuthRepository(get(), get()) }
    single { WarehouseRepository(get()) }
    single { InventoryRepository(get()) }

    // ViewModels
    viewModelOf(::LoginViewModel)
    viewModelOf(::WarehouseViewModel)
    viewModelOf(::InventoryViewModel)
    viewModelOf(::ScannerViewModel)
}

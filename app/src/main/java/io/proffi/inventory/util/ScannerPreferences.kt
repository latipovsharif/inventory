package io.proffi.inventory.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.proffi.inventory.scanner.ScannerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.scannerDataStore: DataStore<Preferences> by preferencesDataStore(name = "scanner_settings")

class ScannerPreferences(private val context: Context) {

    private val SCANNER_TYPE_KEY = stringPreferencesKey("scanner_type")

    val scannerType: Flow<ScannerType> = context.scannerDataStore.data
        .map { preferences ->
            val typeString = preferences[SCANNER_TYPE_KEY] ?: ScannerType.CAMERA.name
            try {
                ScannerType.valueOf(typeString)
            } catch (_: IllegalArgumentException) {
                ScannerType.CAMERA
            }
        }

    suspend fun setScannerType(type: ScannerType) {
        context.scannerDataStore.edit { preferences ->
            preferences[SCANNER_TYPE_KEY] = type.name
        }
    }

    suspend fun getScannerType(): ScannerType {
        return scannerType.first()
    }
}

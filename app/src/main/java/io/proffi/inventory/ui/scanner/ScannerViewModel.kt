package io.proffi.inventory.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.InventoryRepository
import io.proffi.inventory.network.ScanBarcodeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScannedItem(
    val barcode: String,
    val quantity: Double,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ScannerViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Empty)
    val scanState: StateFlow<ScanState> = _scanState

    private val _scannedItems = MutableStateFlow<List<ScannedItem>>(emptyList())
    val scannedItems: StateFlow<List<ScannedItem>> = _scannedItems

    fun scanBarcode(inventoryId: String, barcode: String, quantity: Double) {
        viewModelScope.launch {
            _scanState.value = ScanState.Loading

            val result = inventoryRepository.scanBarcode(inventoryId, barcode, quantity)

            _scanState.value = if (result.isSuccess) {
                val response = result.getOrNull()
                if (response?.body != null) {
                    // Добавляем отсканированный товар в список
                    addScannedItem(
                        barcode = response.body.barcode,
                        quantity = response.body.quantity,
                        message = response.body.message
                    )
                    ScanState.Success(response)
                } else {
                    ScanState.Error("Response body is null")
                }
            } else {
                ScanState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    private fun addScannedItem(barcode: String, quantity: Double, message: String) {
        val newItem = ScannedItem(barcode, quantity, message)
        _scannedItems.value = listOf(newItem) + _scannedItems.value
    }

    suspend fun getCurrentQuantityFromServer(inventoryId: String, barcode: String): Double? {
        return try {
            val result = inventoryRepository.getInventoryItemDetails(inventoryId, barcode)
            if (result.isSuccess) {
                result.getOrNull()?.body?.firstOrNull()?.actualQuantity
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateItemQuantity(barcode: String, timestamp: Long, newQuantity: Double, inventoryId: String) {
        viewModelScope.launch {
            val result = inventoryRepository.updateInventoryItem(inventoryId, barcode, newQuantity)

            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response?.body != null) {
                    val body = response.body
                    _scannedItems.value = _scannedItems.value.map { item ->
                        if (item.barcode == barcode && item.timestamp == timestamp) {
                            item.copy(quantity = body.quantity, message = body.message)
                        } else {
                            item
                        }
                    }
                }
            }
        }
    }

    fun resetScanState() {
        _scanState.value = ScanState.Empty
    }
}

sealed class ScanState {
    object Empty : ScanState()
    object Loading : ScanState()
    data class Success(val response: ScanBarcodeResponse) : ScanState()
    data class Error(val message: String) : ScanState()
}

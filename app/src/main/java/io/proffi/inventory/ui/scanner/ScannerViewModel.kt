package io.proffi.inventory.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.InventoryRepository
import io.proffi.inventory.network.ScanBarcodeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Empty)
    val scanState: StateFlow<ScanState> = _scanState

    fun scanBarcode(inventoryId: String, barcode: String, quantity: Int) {
        viewModelScope.launch {
            _scanState.value = ScanState.Loading

            val result = inventoryRepository.scanBarcode(inventoryId, barcode, quantity)

            _scanState.value = if (result.isSuccess) {
                ScanState.Success(result.getOrNull()!!)
            } else {
                ScanState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
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

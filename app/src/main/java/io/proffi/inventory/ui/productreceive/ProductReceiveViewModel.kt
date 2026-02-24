package io.proffi.inventory.ui.productreceive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.ProductMoveRepository
import io.proffi.inventory.network.ProductMove
import io.proffi.inventory.network.ScanProductMoveResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScannedReceiveItem(
    val barcode: String,
    val quantity: Double,
    val message: String,
    val productName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class ProductReceiveViewModel(
    private val productMoveRepository: ProductMoveRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Empty)
    val scanState: StateFlow<ScanState> = _scanState

    private val _scannedItems = MutableStateFlow<List<ScannedReceiveItem>>(emptyList())
    val scannedItems: StateFlow<List<ScannedReceiveItem>> = _scannedItems

    private val _incomingMovesState = MutableStateFlow<IncomingMovesState>(IncomingMovesState.Loading)
    val incomingMovesState: StateFlow<IncomingMovesState> = _incomingMovesState

    private val _confirmReceiveState = MutableStateFlow<ConfirmReceiveState>(ConfirmReceiveState.Empty)
    val confirmReceiveState: StateFlow<ConfirmReceiveState> = _confirmReceiveState

    fun loadIncomingProductMoves(warehouseId: String) {
        viewModelScope.launch {
            _incomingMovesState.value = IncomingMovesState.Loading

            val result = productMoveRepository.getIncomingProductMoves(warehouseId)

            _incomingMovesState.value = if (result.isSuccess) {
                IncomingMovesState.Success(result.getOrNull()!!)
            } else {
                IncomingMovesState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun scanProductForReceive(moveId: String, barcode: String, quantity: Double) {
        viewModelScope.launch {
            _scanState.value = ScanState.Loading

            val result = productMoveRepository.scanProductForReceive(moveId, barcode, quantity)

            _scanState.value = if (result.isSuccess) {
                val response = result.getOrNull()
                if (response?.body != null) {
                    // Добавляем отсканированный товар в список
                    addScannedItem(
                        barcode = response.body.barcode,
                        quantity = response.body.quantity,
                        message = response.body.message,
                        productName = response.body.productName
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

    private fun addScannedItem(barcode: String, quantity: Double, message: String, productName: String?) {
        val newItem = ScannedReceiveItem(
            barcode = barcode,
            quantity = quantity,
            message = message,
            productName = productName
        )
        _scannedItems.value = listOf(newItem) + _scannedItems.value
    }

    fun confirmReceive(moveId: String) {
        viewModelScope.launch {
            _confirmReceiveState.value = ConfirmReceiveState.Loading

            val result = productMoveRepository.confirmReceiveProductMove(moveId)

            _confirmReceiveState.value = if (result.isSuccess) {
                ConfirmReceiveState.Success(result.getOrNull()!!.message)
            } else {
                ConfirmReceiveState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun resetScanState() {
        _scanState.value = ScanState.Empty
    }

    fun resetConfirmReceiveState() {
        _confirmReceiveState.value = ConfirmReceiveState.Empty
    }

    fun clearScannedItems() {
        _scannedItems.value = emptyList()
    }
}

sealed class ScanState {
    object Empty : ScanState()
    object Loading : ScanState()
    data class Success(val response: ScanProductMoveResponse) : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class IncomingMovesState {
    object Loading : IncomingMovesState()
    data class Success(val moves: List<ProductMove>) : IncomingMovesState()
    data class Error(val message: String) : IncomingMovesState()
}

sealed class ConfirmReceiveState {
    object Empty : ConfirmReceiveState()
    object Loading : ConfirmReceiveState()
    data class Success(val message: String) : ConfirmReceiveState()
    data class Error(val message: String) : ConfirmReceiveState()
}

package io.proffi.inventory.ui.productmove

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.ProductMoveRepository
import io.proffi.inventory.network.ProductMove
import io.proffi.inventory.network.ScanProductMoveResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScannedMoveItem(
    val barcode: String,
    val quantity: Double,
    val message: String,
    val productName: String?,
    val timestamp: Long = System.currentTimeMillis()
)

class ProductMoveViewModel(
    private val productMoveRepository: ProductMoveRepository
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Empty)
    val scanState: StateFlow<ScanState> = _scanState

    private val _scannedItems = MutableStateFlow<List<ScannedMoveItem>>(emptyList())
    val scannedItems: StateFlow<List<ScannedMoveItem>> = _scannedItems

    private val _activeMovesState = MutableStateFlow<ActiveMovesState>(ActiveMovesState.Loading)
    val activeMovesState: StateFlow<ActiveMovesState> = _activeMovesState

    private val _startMoveState = MutableStateFlow<StartMoveState>(StartMoveState.Empty)
    val startMoveState: StateFlow<StartMoveState> = _startMoveState

    private val _completeMoveState = MutableStateFlow<CompleteMoveState>(CompleteMoveState.Empty)
    val completeMoveState: StateFlow<CompleteMoveState> = _completeMoveState

    fun loadActiveProductMoves() {
        viewModelScope.launch {
            _activeMovesState.value = ActiveMovesState.Loading

            val result = productMoveRepository.getActiveProductMoves()

            _activeMovesState.value = if (result.isSuccess) {
                ActiveMovesState.Success(result.getOrNull()!!)
            } else {
                ActiveMovesState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun startProductMove(fromWarehouseId: String, toWarehouseId: String) {
        viewModelScope.launch {
            _startMoveState.value = StartMoveState.Loading

            val result = productMoveRepository.startProductMove(fromWarehouseId, toWarehouseId)

            _startMoveState.value = if (result.isSuccess) {
                StartMoveState.Success(result.getOrNull()!!)
            } else {
                val exception = result.exceptionOrNull()
                if (exception is io.proffi.inventory.data.ProductMoveConflictException) {
                    StartMoveState.Conflict(exception.message ?: "Conflict")
                } else {
                    StartMoveState.Error(exception?.message ?: "Unknown error")
                }
            }
        }
    }

    fun scanProduct(moveId: String, barcode: String, quantity: Double) {
        viewModelScope.launch {
            _scanState.value = ScanState.Loading

            val result = productMoveRepository.scanProductForMove(moveId, barcode, quantity)

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
        val newItem = ScannedMoveItem(
            barcode = barcode,
            quantity = quantity,
            message = message,
            productName = productName
        )
        _scannedItems.value = listOf(newItem) + _scannedItems.value
    }

    fun completeProductMove(moveId: String) {
        viewModelScope.launch {
            _completeMoveState.value = CompleteMoveState.Loading

            val result = productMoveRepository.completeProductMove(moveId)

            _completeMoveState.value = if (result.isSuccess) {
                CompleteMoveState.Success(result.getOrNull()!!.message)
            } else {
                CompleteMoveState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun resetScanState() {
        _scanState.value = ScanState.Empty
    }

    fun resetStartMoveState() {
        _startMoveState.value = StartMoveState.Empty
    }

    fun resetCompleteMoveState() {
        _completeMoveState.value = CompleteMoveState.Empty
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

sealed class ActiveMovesState {
    object Loading : ActiveMovesState()
    data class Success(val moves: List<ProductMove>) : ActiveMovesState()
    data class Error(val message: String) : ActiveMovesState()
}

sealed class StartMoveState {
    object Empty : StartMoveState()
    object Loading : StartMoveState()
    data class Success(val moveId: String) : StartMoveState()
    data class Conflict(val message: String) : StartMoveState()
    data class Error(val message: String) : StartMoveState()
}

sealed class CompleteMoveState {
    object Empty : CompleteMoveState()
    object Loading : CompleteMoveState()
    data class Success(val message: String) : CompleteMoveState()
    data class Error(val message: String) : CompleteMoveState()
}

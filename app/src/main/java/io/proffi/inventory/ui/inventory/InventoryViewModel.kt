package io.proffi.inventory.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.AuthRepository
import io.proffi.inventory.data.InventoryRepository
import io.proffi.inventory.network.Inventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val inventoryRepository: InventoryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _inventoriesState = MutableStateFlow<InventoriesState>(InventoriesState.Empty)
    val inventoriesState: StateFlow<InventoriesState> = _inventoriesState

    private val _startInventoryState = MutableStateFlow<StartInventoryState>(StartInventoryState.Empty)
    val startInventoryState: StateFlow<StartInventoryState> = _startInventoryState

    private val _currentInventoryId = MutableStateFlow<String?>(null)
    val currentInventoryId: StateFlow<String?> = _currentInventoryId

    fun loadOpenInventories(warehouseId: String) {
        viewModelScope.launch {
            _inventoriesState.value = InventoriesState.Loading

            val result = inventoryRepository.getOpenInventories(warehouseId)

            _inventoriesState.value = if (result.isSuccess) {
                InventoriesState.Success(result.getOrNull()!!)
            } else {
                InventoriesState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun startInventory(warehouseId: String) {
        viewModelScope.launch {
            _startInventoryState.value = StartInventoryState.Loading

            val result = inventoryRepository.startInventory(warehouseId)

            if (result.isSuccess) {
                val inventoryId = result.getOrNull()!!
                _currentInventoryId.value = inventoryId
                _startInventoryState.value = StartInventoryState.Success(inventoryId)
            } else {
                _startInventoryState.value = StartInventoryState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun selectExistingInventory(inventoryId: String) {
        _currentInventoryId.value = inventoryId
    }

    fun closeInventory(inventoryId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = inventoryRepository.closeInventory(inventoryId)
            if (result.isSuccess) {
                _currentInventoryId.value = null
                onSuccess()
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun resetStartInventoryState() {
        _startInventoryState.value = StartInventoryState.Empty
    }
}

sealed class InventoriesState {
    object Empty : InventoriesState()
    object Loading : InventoriesState()
    data class Success(val inventories: List<Inventory>) : InventoriesState()
    data class Error(val message: String) : InventoriesState()
}

sealed class StartInventoryState {
    object Empty : StartInventoryState()
    object Loading : StartInventoryState()
    data class Success(val inventoryId: String) : StartInventoryState()
    data class Error(val message: String) : StartInventoryState()
}

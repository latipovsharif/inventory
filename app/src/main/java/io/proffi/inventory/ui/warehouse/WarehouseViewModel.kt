package io.proffi.inventory.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.AuthRepository
import io.proffi.inventory.data.WarehouseRepository
import io.proffi.inventory.network.Warehouse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WarehouseViewModel(
    private val warehouseRepository: WarehouseRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _warehousesState = MutableStateFlow<WarehousesState>(WarehousesState.Loading)
    val warehousesState: StateFlow<WarehousesState> = _warehousesState

    init {
        loadWarehouses()
    }

    fun loadWarehouses() {
        viewModelScope.launch {
            _warehousesState.value = WarehousesState.Loading

            val result = warehouseRepository.getWarehouses()

            _warehousesState.value = if (result.isSuccess) {
                WarehousesState.Success(result.getOrNull()!!)
            } else {
                WarehousesState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}

sealed class WarehousesState {
    object Loading : WarehousesState()
    data class Success(val warehouses: List<Warehouse>) : WarehousesState()
    data class Error(val message: String) : WarehousesState()
}

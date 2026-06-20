package io.proffi.inventory.data
import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.Warehouse
import io.proffi.inventory.network.safeApiCall

class WarehouseRepository(private val apiService: ApiService) {

    suspend fun getWarehouses(): Result<List<Warehouse>> =
        safeApiCall(retries = 2) { apiService.getWarehouses().body.orEmpty() }
}

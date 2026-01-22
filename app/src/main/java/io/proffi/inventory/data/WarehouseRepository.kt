package io.proffi.inventory.data
import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.Warehouse

class WarehouseRepository(private val apiService: ApiService) {

    suspend fun getWarehouses(): Result<List<Warehouse>> {
        return try {
            val response = apiService.getWarehouses()
            Result.success(response.body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

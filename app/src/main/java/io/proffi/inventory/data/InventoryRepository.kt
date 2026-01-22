package io.proffi.inventory.data

import io.proffi.inventory.network.*
import java.text.SimpleDateFormat
import java.util.*

class InventoryRepository(private val apiService: ApiService) {

    suspend fun getOpenInventories(warehouseId: String): Result<List<Inventory>> {
        return try {
            val inventories = apiService.getOpenInventories(warehouseId)
            Result.success(inventories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startInventory(warehouseId: String): Result<String> {
        return try {
            // Генерация текущей даты в ISO 8601 формате: "2026-01-21T10:30:00Z"
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val startDate = dateFormat.format(Date())

            val response = apiService.startInventory(
                StartInventoryRequest(
                    warehouse_id = warehouseId,
                    start_date = startDate
                )
            )
            // Возвращаем только ID инвентаризации из body
            Result.success(response.body.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanBarcode(inventoryId: String, barcode: String, quantity: Int): Result<ScanBarcodeResponse> {
        return try {
            val response = apiService.scanBarcode(
                ScanBarcodeRequest(inventoryId, barcode, quantity)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closeInventory(inventoryId: String): Result<CloseInventoryResponse> {
        return try {
            val response = apiService.closeInventory(inventoryId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

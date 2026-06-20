package io.proffi.inventory.data

import io.proffi.inventory.network.*
import java.text.SimpleDateFormat
import java.util.*

class InventoryRepository(private val apiService: ApiService) {

    suspend fun getOpenInventories(warehouseId: String): Result<List<Inventory>> =
        safeApiCall(retries = 2) { apiService.getOpenInventories(warehouseId).body }

    suspend fun startInventory(warehouseId: String): Result<String> {
        // Текущая дата в ISO 8601: "2026-01-21T10:30:00Z"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val startDate = dateFormat.format(Date())

        return safeApiCall {
            apiService.startInventory(
                StartInventoryRequest(warehouseId = warehouseId, startDate = startDate)
            ).body.id
        }.recoverCatching { e ->
            // HTTP 409 → доменное исключение «существует не закрытая инвентаризация»
            if (e is ApiException && e.code == 409) {
                throw InventoryConflictException(
                    e.serverMessage ?: "Существует не закрытая инвентаризация"
                )
            }
            throw e
        }
    }

    suspend fun scanBarcode(inventoryId: String, barcode: String, quantity: Double): Result<ScanBarcodeResponse> =
        // No retry: scan mutates counts.
        safeApiCall {
            apiService.scanBarcode(
                inventoryId = inventoryId,
                request = ScanBarcodeRequest(barcode = barcode, quantity = quantity)
            )
        }

    suspend fun getInventoryItemDetails(inventoryId: String, barcode: String): Result<InventoryItemDetailsResponse> =
        safeApiCall(retries = 2) { apiService.getInventoryItemDetails(inventoryId, barcode) }

    suspend fun updateInventoryItem(inventoryId: String, barcode: String, quantity: Double): Result<UpdateInventoryItemResponse> =
        safeApiCall {
            // Сначала читаем текущее количество для отправки previous_actual_quantity.
            val detailsResponse = apiService.getInventoryItemDetails(inventoryId, barcode)
            if (detailsResponse.body.isEmpty()) {
                throw IllegalStateException("Product not found in inventory")
            }
            val itemDetail = detailsResponse.body[0]
            apiService.updateInventoryItem(
                inventoryId = inventoryId,
                request = listOf(
                    UpdateInventoryItemRequest(
                        productId = itemDetail.product.id,
                        actualQuantity = quantity,
                        previousActualQuantity = itemDetail.actualQuantity
                    )
                )
            )
        }

    suspend fun closeInventory(inventoryId: String): Result<CloseInventoryResponse> =
        safeApiCall { apiService.closeInventory(inventoryId) }
}

// Пользовательское исключение для конфликта инвентаризации (HTTP 409)
class InventoryConflictException(message: String) : Exception(message)

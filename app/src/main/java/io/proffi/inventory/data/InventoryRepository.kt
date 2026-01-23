package io.proffi.inventory.data

import io.proffi.inventory.network.*
import java.text.SimpleDateFormat
import java.util.*

class InventoryRepository(private val apiService: ApiService) {

    suspend fun getOpenInventories(warehouseId: String): Result<List<Inventory>> {
        return try {
            val response = apiService.getOpenInventories(warehouseId)
            Result.success(response.body)
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
                    warehouseId = warehouseId,
                    startDate = startDate
                )
            )
            // Возвращаем только ID инвентаризации из body
            Result.success(response.body.id)
        } catch (e: retrofit2.HttpException) {
            // Проверяем, является ли ошибка HTTP 409 (Conflict)
            if (e.code() == 409) {
                Result.failure(InventoryConflictException("Существует не закрытая инвентаризация"))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanBarcode(inventoryId: String, barcode: String, quantity: Double): Result<ScanBarcodeResponse> {
        return try {
            val response = apiService.scanBarcode(
                inventoryId = inventoryId,
                request = ScanBarcodeRequest(barcode = barcode, quantity = quantity)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInventoryItemDetails(inventoryId: String, barcode: String): Result<InventoryItemDetailsResponse> {
        return try {
            val response = apiService.getInventoryItemDetails(inventoryId, barcode)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateInventoryItem(inventoryId: String, barcode: String, quantity: Double): Result<UpdateInventoryItemResponse> {
        return try {
            // Сначала получаем текущее количество из сервера для проверки актуальности данных
            val detailsResponse = apiService.getInventoryItemDetails(inventoryId, barcode)

            if (detailsResponse.body.isEmpty()) {
                return Result.failure(Exception("Product not found in inventory"))
            }

            val itemDetail = detailsResponse.body[0]
            val productId = itemDetail.product.id
            val previousQuantity = itemDetail.actualQuantity

            // Отправляем новое количество как массив с product_id, actual_quantity и previous_actual_quantity
            val response = apiService.updateInventoryItem(
                inventoryId = inventoryId,
                request = listOf(
                    UpdateInventoryItemRequest(
                        productId = productId,
                        actualQuantity = quantity,
                        previousActualQuantity = previousQuantity
                    )
                )
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

// Пользовательское исключение для конфликта инвентаризации (HTTP 409)
class InventoryConflictException(message: String) : Exception(message)


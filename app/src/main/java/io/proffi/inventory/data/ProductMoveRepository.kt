package io.proffi.inventory.data

import io.proffi.inventory.network.*
import java.text.SimpleDateFormat
import java.util.*

class ProductMoveRepository(private val apiService: ApiService) {

    suspend fun getActiveProductMoves(): Result<List<ProductMove>> {
        return try {
            val response = apiService.getActiveProductMoves()
            Result.success(response.body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startProductMove(fromWarehouseId: String, toWarehouseId: String): Result<String> {
        return try {
            // Проверка на одинаковые склады
            if (fromWarehouseId == toWarehouseId) {
                return Result.failure(Exception("Склад отправления и назначения не могут быть одинаковыми"))
            }

            // Генерация текущей даты в ISO 8601 формате
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val startDate = dateFormat.format(Date())

            val response = apiService.startProductMove(
                StartProductMoveRequest(
                    fromWarehouseId = fromWarehouseId,
                    toWarehouseId = toWarehouseId,
                    startDate = startDate
                )
            )
            // Возвращаем только ID перемещения из body
            Result.success(response.body.id)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 409) {
                Result.failure(ProductMoveConflictException("Существует незавершённое перемещение"))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanProductForMove(moveId: String, barcode: String, quantity: Double): Result<ScanProductMoveResponse> {
        return try {
            val response = apiService.scanProductForMove(
                moveId = moveId,
                request = ScanProductMoveRequest(barcode = barcode, quantity = quantity)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeProductMove(moveId: String): Result<CompleteProductMoveResponse> {
        return try {
            val response = apiService.completeProductMove(moveId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Receive functions
    suspend fun getIncomingProductMoves(warehouseId: String): Result<List<ProductMove>> {
        return try {
            val response = apiService.getIncomingProductMoves(warehouseId)
            Result.success(response.body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanProductForReceive(moveId: String, barcode: String, quantity: Double): Result<ScanProductMoveResponse> {
        return try {
            val response = apiService.scanProductForReceive(
                moveId = moveId,
                request = ScanProductMoveRequest(barcode = barcode, quantity = quantity)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmReceiveProductMove(moveId: String): Result<CompleteProductMoveResponse> {
        return try {
            val response = apiService.confirmReceiveProductMove(moveId)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Custom exception для конфликтов при создании перемещения
class ProductMoveConflictException(message: String) : Exception(message)

package io.proffi.inventory.data

import io.proffi.inventory.network.*
import java.text.SimpleDateFormat
import java.util.*

class ProductMoveRepository(private val apiService: ApiService) {

    suspend fun getActiveProductMoves(): Result<List<ProductMove>> =
        safeApiCall(retries = 2) { apiService.getActiveProductMoves().body.orEmpty() }

    suspend fun startProductMove(fromWarehouseId: String, toWarehouseId: String): Result<String> {
        if (fromWarehouseId == toWarehouseId) {
            return Result.failure(Exception("Склад отправления и назначения не могут быть одинаковыми"))
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val startDate = dateFormat.format(Date())

        return safeApiCall {
            apiService.startProductMove(
                StartProductMoveRequest(
                    fromWarehouseId = fromWarehouseId,
                    toWarehouseId = toWarehouseId,
                    startDate = startDate
                )
            ).body.id
        }.recoverCatching { e ->
            if (e is ApiException && e.code == 409) {
                throw ProductMoveConflictException(
                    e.serverMessage ?: "Существует незавершённое перемещение"
                )
            }
            throw e
        }
    }

    suspend fun scanProductForMove(moveId: String, barcode: String, quantity: Double): Result<ScanProductMoveResponse> =
        safeApiCall {
            apiService.scanProductForMove(
                moveId = moveId,
                request = ScanProductMoveRequest(barcode = barcode, quantity = quantity)
            )
        }

    suspend fun completeProductMove(moveId: String): Result<CompleteProductMoveResponse> =
        safeApiCall { apiService.completeProductMove(moveId) }

    // Receive functions
    suspend fun getIncomingProductMoves(warehouseId: String): Result<List<ProductMove>> =
        safeApiCall(retries = 2) { apiService.getIncomingProductMoves(warehouseId).body.orEmpty() }

    suspend fun scanProductForReceive(moveId: String, barcode: String, quantity: Double): Result<ScanProductMoveResponse> =
        safeApiCall {
            apiService.scanProductForReceive(
                moveId = moveId,
                request = ScanProductMoveRequest(barcode = barcode, quantity = quantity)
            )
        }

    suspend fun confirmReceiveProductMove(moveId: String): Result<CompleteProductMoveResponse> =
        safeApiCall { apiService.confirmReceiveProductMove(moveId) }
}

// Custom exception для конфликтов при создании перемещения
class ProductMoveConflictException(message: String) : Exception(message)

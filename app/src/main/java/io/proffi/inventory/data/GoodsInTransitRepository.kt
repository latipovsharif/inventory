package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.GitDetailResponse
import io.proffi.inventory.network.GitDocument
import io.proffi.inventory.network.GitScanRequest
import io.proffi.inventory.network.GitScanResponse

class GoodsInTransitRepository(private val apiService: ApiService) {

    /** Документы «товар в пути» для склада (фильтр статусов делает ViewModel). */
    suspend fun getDocuments(warehouseId: String): Result<List<GitDocument>> {
        return try {
            val response = apiService.getGoodsInTransit(warehouseId)
            Result.success(response.body.orEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDetail(id: String): Result<GitDetailResponse> {
        return try {
            Result.success(apiService.getGoodsInTransitDetail(id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scan(documentId: String, barcode: String, boxCode: String, quantity: Double): Result<GitScanResponse> {
        return try {
            val response = apiService.scanGoodsInTransit(
                id = documentId,
                request = GitScanRequest(barcode = barcode, boxCode = boxCode, quantity = quantity)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finalize(documentId: String): Result<Unit> {
        return try {
            apiService.finalizeGoodsInTransit(documentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancel(documentId: String): Result<Unit> {
        return try {
            apiService.cancelGoodsInTransit(documentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

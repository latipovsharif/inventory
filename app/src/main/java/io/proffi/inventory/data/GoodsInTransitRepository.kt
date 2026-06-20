package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.GitDetailResponse
import io.proffi.inventory.network.GitDocument
import io.proffi.inventory.network.GitScanRequest
import io.proffi.inventory.network.GitScanResponse
import io.proffi.inventory.network.safeApiCall

class GoodsInTransitRepository(private val apiService: ApiService) {

    private companion object {
        // Safety cap so a misbehaving page_count can't loop forever.
        const val MAX_PAGES = 100
    }

    /** Документы «товар в пути» для склада (фильтр статусов делает ViewModel). */
    suspend fun getDocuments(warehouseId: String): Result<List<GitDocument>> =
        safeApiCall {
            val all = mutableListOf<GitDocument>()
            var page = 1
            var pageCount = 1
            do {
                val response = apiService.getGoodsInTransit(warehouseId, page)
                val items = response.body.orEmpty()
                all.addAll(items)
                pageCount = response.pageCount
                page++
                if (items.isEmpty()) break
            } while (page <= pageCount && page <= MAX_PAGES)
            all
        }

    suspend fun getDetail(id: String): Result<GitDetailResponse> =
        safeApiCall(retries = 2) { apiService.getGoodsInTransitDetail(id) }

    suspend fun scan(documentId: String, barcode: String, boxCode: String, quantity: Double): Result<GitScanResponse> =
        // No retry: scan mutates received quantities.
        safeApiCall {
            apiService.scanGoodsInTransit(
                id = documentId,
                request = GitScanRequest(barcode = barcode, boxCode = boxCode, quantity = quantity)
            )
        }

    suspend fun finalize(documentId: String): Result<Unit> =
        safeApiCall { apiService.finalizeGoodsInTransit(documentId); Unit }

    suspend fun cancel(documentId: String): Result<Unit> =
        safeApiCall { apiService.cancelGoodsInTransit(documentId); Unit }
}

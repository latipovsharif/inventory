package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.PackProductRequest
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationsListResponse
import io.proffi.inventory.network.safeApiCall

class PackingRepository(private val apiService: ApiService) {

    suspend fun getPackingRecommendations(
        page: Int,
        status: String = "packaging"
    ): Result<RecommendationsListResponse> =
        safeApiCall(retries = 2) { apiService.getRecommendationsFiltered(page, status) }

    suspend fun getRecommendationDetail(id: String): Result<RecommendationDetail> =
        safeApiCall(retries = 2) { apiService.getRecommendationDetail(id) }

    suspend fun packProduct(
        recommendationId: String,
        boxCode: String,
        barcode: String,
        quantity: Int = 1
    ): Result<RecommendationDetail> =
        // No retry: pack mutates state, a retried POST could double-count.
        safeApiCall {
            apiService.packProduct(
                id = recommendationId,
                request = PackProductRequest(
                    boxCode = boxCode,
                    barcode = barcode,
                    quantity = quantity
                )
            )
        }
}

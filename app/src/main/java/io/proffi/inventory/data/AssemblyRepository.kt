package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.CollectRequest
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationsListResponse
import io.proffi.inventory.network.safeApiCall

class AssemblyRepository(private val apiService: ApiService) {

    suspend fun getRecommendations(page: Int, status: String? = null): Result<RecommendationsListResponse> =
        safeApiCall(retries = 2) {
            if (status != null) {
                apiService.getRecommendationsFiltered(page, status)
            } else {
                apiService.getRecommendations(page)
            }
        }

    suspend fun getRecommendationDetail(id: String): Result<RecommendationDetail> =
        safeApiCall(retries = 2) { apiService.getRecommendationDetail(id) }

    suspend fun collectProduct(
        recommendationId: String,
        boxId: String,
        productId: String,
        quantity: Double
    ): Result<RecommendationDetail> =
        // No retry: collect mutates stock, a retried POST could double-count.
        safeApiCall {
            apiService.collectProduct(
                id = recommendationId,
                request = CollectRequest(boxId = boxId, productId = productId, quantity = quantity)
            )
        }
}

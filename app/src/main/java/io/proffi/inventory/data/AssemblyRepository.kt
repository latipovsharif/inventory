package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.CollectRequest
import io.proffi.inventory.network.Recommendation
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationsListResponse

class AssemblyRepository(private val apiService: ApiService) {

    suspend fun getRecommendations(page: Int): Result<RecommendationsListResponse> {
        return try {
            val response = apiService.getRecommendations(page)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecommendationDetail(id: String): Result<RecommendationDetail> {
        return try {
            val response = apiService.getRecommendationDetail(id)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun collectProduct(
        recommendationId: String,
        boxId: String,
        productId: String,
        quantity: Double
    ): Result<RecommendationDetail> {
        return try {
            val response = apiService.collectProduct(
                id = recommendationId,
                request = CollectRequest(boxId = boxId, productId = productId, quantity = quantity)
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


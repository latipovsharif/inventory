package io.proffi.inventory.data

import io.proffi.inventory.network.ApiService
import io.proffi.inventory.network.PackProductRequest
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationsListResponse

class PackingRepository(private val apiService: ApiService) {

    suspend fun getPackingRecommendations(
        page: Int,
        status: String = "packaging"
    ): Result<RecommendationsListResponse> {
        return try {
            val response = apiService.getRecommendationsFiltered(page, status)
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

    suspend fun packProduct(
        recommendationId: String,
        boxCode: String,
        barcode: String,
        quantity: Int = 1
    ): Result<RecommendationDetail> {
        return try {
            val response = apiService.packProduct(
                id = recommendationId,
                request = PackProductRequest(
                    boxCode = boxCode,
                    barcode = barcode,
                    quantity = quantity
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

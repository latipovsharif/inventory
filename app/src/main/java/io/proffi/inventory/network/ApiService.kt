package io.proffi.inventory.network


import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface ApiService {
    @POST("api/v1/authorization/login/")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse

    @POST("api/v1/authorization/refresh/")
    suspend fun refreshToken(@Body refreshRequest: RefreshTokenRequest): LoginResponse

    @GET("api/v1/warehouses/warehouse")
    suspend fun getWarehouses(): WarehousesResponse

    @GET("api/v1/warehouses/inventory")
    suspend fun getOpenInventories(@Query("warehouse_id") warehouseId: String): InventoryListResponse

    @GET("api/v1/warehouses/inventory/{id}/details")
    suspend fun getInventoryItemDetails(
        @Path("id") inventoryId: String,
        @Query("barcode") barcode: String
    ): InventoryItemDetailsResponse

    @POST("/api/v1/warehouses/inventory")
    suspend fun startInventory(@Body request: StartInventoryRequest): StartInventoryResponse

    @POST("api/v1/warehouses/inventory/{id}/scan")
    suspend fun scanBarcode(@Path("id") inventoryId: String, @Body request: ScanBarcodeRequest): ScanBarcodeResponse

    @PUT("api/v1/warehouses/inventory/{id}/update")
    suspend fun updateInventoryItem(@Path("id") inventoryId: String, @Body request: List<UpdateInventoryItemRequest>): UpdateInventoryItemResponse

    @POST("api/inventories/{id}/close")
    suspend fun closeInventory(@Path("id") inventoryId: String): CloseInventoryResponse

    // Product Move endpoints
    @POST("api/v1/warehouses/product-move")
    suspend fun startProductMove(@Body request: StartProductMoveRequest): StartProductMoveResponse

    @GET("api/v1/warehouses/product-move")
    suspend fun getActiveProductMoves(): ProductMoveListResponse

    @POST("api/v1/warehouses/product-move/{id}/scan")
    suspend fun scanProductForMove(@Path("id") moveId: String, @Body request: ScanProductMoveRequest): ScanProductMoveResponse

    @POST("api/v1/warehouses/product-move/{id}/complete")
    suspend fun completeProductMove(@Path("id") moveId: String): CompleteProductMoveResponse

    // Product Receive endpoints
    @GET("api/v1/documents/product-move")
    suspend fun getIncomingProductMoves(@Query("warehouse_to") warehouseTo: String): ProductMoveListResponse

    @POST("api/v1/documents/product-move/{id}/scan")
    suspend fun scanProductForReceive(@Path("id") moveId: String, @Body request: ScanProductMoveRequest): ScanProductMoveResponse

    @POST("api/v1/warehouses/product-move/{id}/confirm-receive")
    suspend fun confirmReceiveProductMove(@Path("id") moveId: String): CompleteProductMoveResponse

    // Assembly (picking) endpoints
    @GET("api/v1/warehouses/recommendations")
    suspend fun getRecommendations(@Query("page") page: Int): RecommendationsListResponse

    @GET("api/v1/warehouses/recommendations/{id}")
    suspend fun getRecommendationDetail(@Path("id") id: String): RecommendationDetail

    @POST("api/v1/warehouses/recommendations/{id}/collect")
    suspend fun collectProduct(
        @Path("id") id: String,
        @Body request: CollectRequest
    ): RecommendationDetail
}

// Request/Response models
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("expires_in")
    val expiresIn: Int,
    val message: String,
    val status: Int,
    @SerializedName("token_type")
    val tokenType: String,
    val user: User
)

data class User(
    val email: String,
    val id: String
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

// Warehouse models
data class WarehousesResponse(
    @SerializedName("page_count")
    val pageCount: Int,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("item_per_page")
    val itemPerPage: Int,
    val body: List<Warehouse>
)

data class Warehouse(
    val id: String,
    val store: Store,
    val name: String,
    val address: Address
)

data class Store(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?
)

data class Address(
    val id: String,
    val country: Country,
    @SerializedName("address_string")
    val addressString: String,
    @SerializedName("zip_code")
    val zipCode: String?
)

data class Country(
    val id: String,
    val name: String,
    val code: String
)

// Inventory models
data class StartInventoryResponse(
    val status: Int,
    val message: String,
    val body: InventoryBody
)

data class InventoryBody(
    val id: String
)

data class InventoryListResponse(
    @SerializedName("page_count")
    val pageCount: Int,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("item_per_page")
    val itemPerPage: Int,
    val body: List<Inventory>
)

data class Inventory(
    val id: String,
    val warehouse: WarehouseInfo,
    @SerializedName("start_date")
    val startDate: String,
    @SerializedName("end_date")
    val endDate: String?,
    val status: String,
    @SerializedName("created_by")
    val createdBy: UserInfo,
    @SerializedName("created_at")
    val createdAt: String
)

data class WarehouseInfo(
    val id: String,
    val name: String
)

data class UserInfo(
    val id: String,
    @SerializedName("first_name")
    val firstName: String,
    @SerializedName("last_name")
    val lastName: String,
    val email: String
)

// Inventory item details models
data class InventoryItemDetailsResponse(
    @SerializedName("page_count")
    val pageCount: Int,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("item_per_page")
    val itemPerPage: Int,
    val body: List<InventoryItemDetail>
)

data class InventoryItemDetail(
    val id: String,
    val inventory: String?,
    val product: ProductDetail,
    @SerializedName("expected_quantity")
    val expectedQuantity: Double,
    @SerializedName("actual_quantity")
    val actualQuantity: Double,
    val difference: Double
)

data class ProductDetail(
    val id: String,
    val name: String,
    val barcode: String,
    val article: String,
    val origin: String?,
    val description: String?,
    val size: String?,
    val state: String?,
    val category: CategoryDetail?,
    @SerializedName("product_group")
    val productGroup: String?,
    val images: String?
)

data class CategoryDetail(
    val id: String,
    val code: String,
    val name: String,
    val parent: String?,
    val children: String?,
    val image: String?,
    @SerializedName("show_on_cash")
    val showOnCash: Boolean
)


data class StartInventoryRequest(
    @SerializedName("warehouse_id")
    val warehouseId: String,
    @SerializedName("start_date")
    val startDate: String  // Формат ISO 8601: "2026-01-15T10:30:00Z"
)

data class ScanBarcodeRequest(
    val barcode: String,
    val quantity: Double  // Изменено с Int на Double для поддержки дробных значений
)

data class ScanBarcodeResponse(
    val status: Int,
    val message: String,
    val body: ScanBarcodeBody
)

data class ScanBarcodeBody(
    val barcode: String,
    val message: String,
    val quantity: Double
)

data class UpdateInventoryItemRequest(
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("actual_quantity")
    val actualQuantity: Double,
    @SerializedName("previous_actual_quantity")
    val previousActualQuantity: Double
)

data class UpdateInventoryItemResponse(
    val status: Int,
    val message: String,
    val body: UpdateInventoryItemBody
)

data class UpdateInventoryItemBody(
    val barcode: String,
    val message: String,
    val quantity: Double
)

data class CloseInventoryResponse(
    val success: Boolean,
    val message: String?
)

// Product Move models
data class StartProductMoveRequest(
    @SerializedName("from_warehouse_id")
    val fromWarehouseId: String,
    @SerializedName("to_warehouse_id")
    val toWarehouseId: String,
    @SerializedName("start_date")
    val startDate: String  // Формат ISO 8601: "2026-02-06T10:30:00Z"
)

data class StartProductMoveResponse(
    val status: Int,
    val message: String,
    val body: ProductMoveBody
)

data class ProductMoveBody(
    val id: String
)

data class ProductMoveListResponse(
    @SerializedName("page_count")
    val pageCount: Int,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("item_per_page")
    val itemPerPage: Int,
    val body: List<ProductMove>
)

data class ProductMove(
    val id: String,
    val status: String,
    @SerializedName("document_number")
    val documentNumber: String,
    @SerializedName("warehouse_from")
    val warehouseFrom: Warehouse,
    @SerializedName("warehouse_to")
    val warehouseTo: Warehouse,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("shipped_at")
    val shippedAt: String?,
    @SerializedName("received_at")
    val receivedAt: String?,
    @SerializedName("selected_date")
    val selectedDate: String,
    @SerializedName("created_at")
    val createdAt: String
)

data class ScanProductMoveRequest(
    val barcode: String,
    val quantity: Double
)

data class ScanProductMoveResponse(
    val status: Int,
    val message: String,
    val body: ScanProductMoveBody
)

data class ScanProductMoveBody(
    val barcode: String,
    val message: String,
    val quantity: Double,
    @SerializedName("product_name")
    val productName: String?
)

data class CompleteProductMoveResponse(
    val status: Int,
    val message: String
)

// Assembly (Picking) models
data class RecommendationsListResponse(
    @SerializedName("page_count")
    val pageCount: Int,
    @SerializedName("total_items")
    val totalItems: Int,
    @SerializedName("item_per_page")
    val itemPerPage: Int,
    val body: List<Recommendation>
)

data class Recommendation(
    val id: String,
    @SerializedName("from_warehouse")
    val fromWarehouse: WarehouseInfo,
    @SerializedName("to_warehouse")
    val toWarehouse: WarehouseInfo,
    val status: String,
    val notes: String?,
    @SerializedName("created_by")
    val createdBy: UserInfo,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("total_products")
    val totalProducts: Int,
    @SerializedName("total_requested")
    val totalRequested: Int
)

data class RecommendationDetail(
    val id: String,
    @SerializedName("from_warehouse")
    val fromWarehouse: WarehouseInfo,
    @SerializedName("to_warehouse")
    val toWarehouse: WarehouseInfo,
    val status: String,
    val notes: String?,
    @SerializedName("created_by")
    val createdBy: UserInfo,
    @SerializedName("created_at")
    val createdAt: String,
    val details: List<RecommendationDetailItem>
)

data class RecommendationDetailItem(
    val id: String,
    val product: RecommendationProduct,
    @SerializedName("requested_quantity")
    val requestedQuantity: Int,
    @SerializedName("collected_quantity")
    val collectedQuantity: Int?,
    val locations: List<RecommendationLocation>
)

data class RecommendationProduct(
    val id: String,
    val name: String,
    val barcode: String,
    val article: String
)

data class RecommendationLocation(
    val id: String,
    val quantity: Int,
    val box: LocationItem,
    val shelf: LocationItem,
    val zone: LocationItem
)

data class LocationItem(
    val id: String,
    val name: String,
    val code: String
)

data class CollectRequest(
    @SerializedName("box_id")
    val boxId: String,
    @SerializedName("product_id")
    val productId: String,
    val quantity: Double
)


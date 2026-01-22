package io.proffi.inventory.network


import retrofit2.http.*

interface ApiService {
    @POST("api/v1/authorization/login/")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse

    @POST("api/v1/authorization/refresh/")
    suspend fun refreshToken(@Body refreshRequest: RefreshTokenRequest): LoginResponse

    @GET("api/v1/warehouses/warehouse")
    suspend fun getWarehouses(): WarehousesResponse

    @GET("/api/v1/warehouses/inventory")
    suspend fun getOpenInventories(@Query("warehouseId") warehouseId: String): List<Inventory>

    @POST("api/inventories/start")
    suspend fun startInventory(@Body request: StartInventoryRequest): StartInventoryResponse

    @POST("api/inventories/scan")
    suspend fun scanBarcode(@Body request: ScanBarcodeRequest): ScanBarcodeResponse

    @POST("api/inventories/{id}/close")
    suspend fun closeInventory(@Path("id") inventoryId: String): CloseInventoryResponse
}

// Request/Response models
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int,
    val message: String,
    val status: Int,
    val token_type: String,
    val user: User
)

data class User(
    val email: String,
    val id: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

// Warehouse models
data class WarehousesResponse(
    val page_count: Int,
    val total_items: Int,
    val item_per_page: Int,
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
    val address_string: String,
    val zip_code: String?
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

data class Inventory(
    val id: String,
    val warehouseId: String,
    val warehouseName: String?,
    val startedAt: String,
    val status: String
)

data class StartInventoryRequest(
    val warehouse_id: String,
    val start_date: String  // Формат ISO 8601: "2026-01-15T10:30:00Z"
)

data class ScanBarcodeRequest(
    val inventoryId: String,
    val barcode: String,
    val quantity: Int
)

data class ScanBarcodeResponse(
    val success: Boolean,
    val message: String?,
    val productName: String?
)

data class CloseInventoryResponse(
    val success: Boolean,
    val message: String?
)

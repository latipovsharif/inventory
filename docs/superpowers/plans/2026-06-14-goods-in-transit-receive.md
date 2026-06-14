# Приход товара в пути + распределение по складу — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android ТСД-экран приёмки supplier-документов «товар в пути» со сканированием товаров в ячейки склада и финализацией прихода, на существующем `goods_in_transit` API + мелкое обогащение ответов backend именами товаров.

**Architecture:** Backend (Go, cloudmarket-server) уже реализует весь цикл `in_transit → receiving → completed`; добавляем только имена товаров в detail/scan-ответы. Android (Kotlin, inventory) получает новый пакет `ui/goodsintransit` по образцу `ui/packing`: Retrofit `ApiService` → `GoodsInTransitRepository` → `GoodsInTransitViewModel` → две Compose-Activity (выбор склада+список, фазовый скан-экран). Раскладка по складу = обязательный `box_code` на каждом скане.

**Tech Stack:** Go + pgx (backend), Kotlin + Jetpack Compose + Retrofit/Gson + Koin + Coroutines/StateFlow (Android).

**Репозитории:**
- Backend: `cloudmarket-server` (в WSL: `/home/market/projects/cloudmarket-server`; из Windows: `//wsl.localhost/ubuntu-26.04/home/market/projects/cloudmarket-server`).
- Android: `inventory` (текущий, `C:/Users/latip/OneDrive/Документы/projects/inventory`).

**Порядок:** сначала backend (Задачи 1–2) — он даёт имена товаров. Затем Android (Задачи 3–10).

---

## Backend (cloudmarket-server)

> Go-команды запускать из корня репозитория cloudmarket-server в WSL.
> Файлы редактировать по UNC-пути из Windows или напрямую в WSL.

### Task 1: Detail-ответ отдаёт имя/штрихкод/артикул товара

**Files:**
- Modify: `documents/goods_in_transit.go` (структуры `GoodsInTransitDetail`, `GoodsInTransitScan`)
- Modify: `documents/goods_in_transit_serializers.go` (`GITDetailLine`, `GITScanLine`)
- Modify: `documents/goods_in_transit_repo.go` (`ListGITDetails`, `ListGITScans`, `GITDetail`)
- Test: `documents/goods_in_transit_integration_test.go`

- [ ] **Step 1: Write the failing test**

Добавить в конец `documents/goods_in_transit_integration_test.go`:

```go
func TestGITDetailReturnsProductInfo(t *testing.T) {
	ctx, tx := testdb.TxOrSkip(t)
	f := seedStockFixture(t, ctx, tx)
	cpID := seedZeroCounterparty(t, ctx, tx)
	if _, err := tx.Exec(ctx, `UPDATE products SET barcode='GIT-DET-BC' WHERE id=$1`, f.productID); err != nil {
		t.Skipf("set barcode: %v", err)
	}

	// expected name/article straight from the products row
	var wantName, wantArticle string
	if err := tx.QueryRow(ctx, `SELECT name, article FROM products WHERE id=$1`, f.productID).
		Scan(&wantName, &wantArticle); err != nil {
		t.Fatalf("read product: %v", err)
	}

	svc := newGITService()
	tpl := excel.Template{
		ExternalDocumentNumber: "INV-GIT-DET", Counterparty: cpID, Warehouse: f.warehouseID, CreatedBy: f.userID,
		Details: []excel.TemplateDetail{{ProductName: "P", Barcode: "GIT-DET-BC", Cost: 10, Quantity: 5, SellPrice: 20}},
	}
	gitID, err := svc.createGoodsInTransitFromExcelTx(ctx, tx, tpl)
	if err != nil {
		t.Fatalf("create: %v", err)
	}

	view, err := svc.repo.GITDetail(ctx, tx, gitID)
	if err != nil {
		t.Fatalf("GITDetail: %v", err)
	}
	if len(view.Details) != 1 {
		t.Fatalf("details len=%d, want 1", len(view.Details))
	}
	d := view.Details[0]
	if d.ProductName != wantName || d.Article != wantArticle || d.Barcode != "GIT-DET-BC" {
		t.Fatalf("detail enrichment got name=%q article=%q barcode=%q, want %q/%q/GIT-DET-BC",
			d.ProductName, d.Article, d.Barcode, wantName, wantArticle)
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./documents/ -run TestGITDetailReturnsProductInfo -v`
Expected: FAIL — компиляция падает на `d.ProductName`/`d.Article`/`d.Barcode` (unknown field) либо assert (поля пустые). Если нет тестовой БД — `SKIP` (тогда полагаемся на компиляцию: `go build ./documents/` должен пройти после Step 3).

- [ ] **Step 3: Add fields to model + serializer structs**

В `documents/goods_in_transit.go` заменить структуры `GoodsInTransitDetail` и `GoodsInTransitScan`:

```go
// GoodsInTransitDetail is a goods_in_transit_details row.
type GoodsInTransitDetail struct {
	ID               string
	ProductID        string
	ProductName      string
	Barcode          string
	Article          string
	OrderedQuantity  float64
	ReceivedQuantity float64
	InvoicePrice     float64
	SellPrice        float64
}

// GoodsInTransitScan is a goods_in_transit_scans row joined with its detail's product.
type GoodsInTransitScan struct {
	ProductID      string
	ProductName    string
	WarehouseBoxID string
	Quantity       float64
}
```

В `documents/goods_in_transit_serializers.go` заменить `GITDetailLine` и `GITScanLine`:

```go
// GITDetailLine is a detail line in the detail view.
type GITDetailLine struct {
	ProductID        string  `json:"product_id"`
	ProductName      string  `json:"product_name"`
	Barcode          string  `json:"barcode"`
	Article          string  `json:"article"`
	OrderedQuantity  float64 `json:"ordered_quantity"`
	ReceivedQuantity float64 `json:"received_quantity"`
	InvoicePrice     float64 `json:"invoice_price"`
	SellPrice        float64 `json:"sell_price"`
}

// GITScanLine is a scan in the detail view.
type GITScanLine struct {
	ProductID      string  `json:"product_id"`
	ProductName    string  `json:"product_name"`
	WarehouseBoxID string  `json:"warehouse_box_id"`
	Quantity       float64 `json:"quantity"`
}
```

- [ ] **Step 4: JOIN products in repo queries + map fields**

В `documents/goods_in_transit_repo.go` заменить `ListGITDetails` (текущий select без имён):

```go
// ListGITDetails returns all detail rows for a header (with product info).
func (r *DocumentRepo) ListGITDetails(ctx context.Context, db base.PGXDB, gitID string) ([]GoodsInTransitDetail, error) {
	rows, err := db.Query(ctx,
		`SELECT d.id::text, d.product_id::text, p.name, p.barcode, p.article,
		        d.ordered_quantity, d.received_quantity, d.invoice_price, d.sell_price
		 FROM goods_in_transit_details d
		 JOIN products p ON p.id = d.product_id
		 WHERE d.document_goods_in_transit_id = $1`,
		gitID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []GoodsInTransitDetail
	for rows.Next() {
		var d GoodsInTransitDetail
		if err := rows.Scan(&d.ID, &d.ProductID, &d.ProductName, &d.Barcode, &d.Article,
			&d.OrderedQuantity, &d.ReceivedQuantity, &d.InvoicePrice, &d.SellPrice); err != nil {
			return nil, err
		}
		out = append(out, d)
	}
	return out, rows.Err()
}
```

Заменить `ListGITScans`:

```go
// ListGITScans returns all scans for a header joined to their detail's product.
func (r *DocumentRepo) ListGITScans(ctx context.Context, db base.PGXDB, gitID string) ([]GoodsInTransitScan, error) {
	rows, err := db.Query(ctx,
		`SELECT d.product_id::text, p.name, s.warehouse_box_id::text, s.quantity
		 FROM goods_in_transit_scans s
		 JOIN goods_in_transit_details d ON d.id = s.detail_id
		 JOIN products p ON p.id = d.product_id
		 WHERE d.document_goods_in_transit_id = $1`,
		gitID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []GoodsInTransitScan
	for rows.Next() {
		var s GoodsInTransitScan
		if err := rows.Scan(&s.ProductID, &s.ProductName, &s.WarehouseBoxID, &s.Quantity); err != nil {
			return nil, err
		}
		out = append(out, s)
	}
	return out, rows.Err()
}
```

В `GITDetail` (та же файл) заменить тела двух циклов маппинга на:

```go
	for _, d := range details {
		v.Details = append(v.Details, GITDetailLine{
			ProductID: d.ProductID, ProductName: d.ProductName, Barcode: d.Barcode, Article: d.Article,
			OrderedQuantity: d.OrderedQuantity, ReceivedQuantity: d.ReceivedQuantity,
			InvoicePrice: d.InvoicePrice, SellPrice: d.SellPrice,
		})
	}
```

и

```go
	for _, sc := range scans {
		v.Scans = append(v.Scans, GITScanLine{
			ProductID: sc.ProductID, ProductName: sc.ProductName,
			WarehouseBoxID: sc.WarehouseBoxID, Quantity: sc.Quantity,
		})
	}
```

> Примечание: `finalizeGoodsInTransitTx` использует `ListGITDetails`/`ListGITScans` только по полям ProductID/ReceivedQuantity/InvoicePrice/SellPrice/WarehouseBoxID/Quantity — новые поля его не ломают.

- [ ] **Step 5: Run test to verify it passes**

Run: `go test ./documents/ -run TestGITDetailReturnsProductInfo -v`
Expected: PASS (или SKIP без БД). Также `go build ./documents/` — без ошибок.

- [ ] **Step 6: Commit**

```bash
git add documents/goods_in_transit.go documents/goods_in_transit_serializers.go documents/goods_in_transit_repo.go documents/goods_in_transit_integration_test.go
git commit -m "feat(goods-in-transit): enrich detail view with product name/barcode/article"
```

---

### Task 2: Scan-ответ возвращает имя товара

**Files:**
- Modify: `documents/goods_in_transit_repo.go` (`GITProductByBarcode`)
- Modify: `documents/goods_in_transit_service.go` (`scanGoodsInTransitTx` caller, `ScanGoodsInTransit`)
- Modify: `documents/goods_in_transit_controller.go` (`GoodsInTransitScan`)
- Test: `documents/goods_in_transit_integration_test.go`

- [ ] **Step 1: Write the failing test**

Добавить в `documents/goods_in_transit_integration_test.go`:

```go
func TestGITProductByBarcodeReturnsName(t *testing.T) {
	ctx, tx := testdb.TxOrSkip(t)
	f := seedStockFixture(t, ctx, tx)
	if _, err := tx.Exec(ctx, `UPDATE products SET barcode='GIT-PN-BC' WHERE id=$1`, f.productID); err != nil {
		t.Skipf("set barcode: %v", err)
	}
	var wantName string
	if err := tx.QueryRow(ctx, `SELECT name FROM products WHERE id=$1`, f.productID).Scan(&wantName); err != nil {
		t.Fatalf("read name: %v", err)
	}

	repo := &DocumentRepo{}
	id, name, err := repo.GITProductByBarcode(ctx, tx, "GIT-PN-BC")
	if err != nil {
		t.Fatalf("GITProductByBarcode: %v", err)
	}
	if id != f.productID || name != wantName {
		t.Fatalf("got id=%q name=%q, want %q/%q", id, name, f.productID, wantName)
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `go test ./documents/ -run TestGITProductByBarcodeReturnsName -v`
Expected: FAIL — компиляция: `GITProductByBarcode` возвращает 2 значения, не 3.

- [ ] **Step 3: Add name to GITProductByBarcode**

В `documents/goods_in_transit_repo.go` заменить `GITProductByBarcode`:

```go
// GITProductByBarcode resolves a barcode to a product id and name.
func (r *DocumentRepo) GITProductByBarcode(ctx context.Context, db base.PGXDB, barcode string) (string, string, error) {
	var id, name string
	err := db.QueryRow(ctx,
		`SELECT id::text, name FROM products WHERE barcode = $1 AND deleted_at IS NULL`,
		barcode).Scan(&id, &name)
	return id, name, err
}
```

- [ ] **Step 4: Update scanGoodsInTransitTx caller + ScanGoodsInTransit wrapper**

В `documents/goods_in_transit_service.go`, внутри `scanGoodsInTransitTx`, заменить вызов резолва штрихкода:

```go
	productID, _, err := s.repo.GITProductByBarcode(ctx, tx, barcode)
	if err != nil {
		return base.NewError(http.StatusBadRequest, fmt.Sprintf("unknown barcode %q", barcode), nil)
	}
```

Заменить публичную обёртку `ScanGoodsInTransit` (вернуть имя товара):

```go
// ScanGoodsInTransit records a single ТСД scan and returns the scanned product's name.
func (s *DocumentService) ScanGoodsInTransit(ctx context.Context, pool *pgxpool.Pool, gitID, barcode, boxCode string, qty float64) (string, error) {
	tx, err := pool.Begin(ctx)
	if err != nil {
		return "", fmt.Errorf("cannot begin transaction: %w", err)
	}
	defer tx.Rollback(ctx) //nolint:errcheck
	if err := s.scanGoodsInTransitTx(ctx, tx, gitID, barcode, boxCode, qty); err != nil {
		return "", err
	}
	_, name, err := s.repo.GITProductByBarcode(ctx, tx, barcode)
	if err != nil {
		return "", fmt.Errorf("cannot resolve product name: %w", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return "", err
	}
	return name, nil
}
```

- [ ] **Step 5: Controller returns body with product_name**

В `documents/goods_in_transit_controller.go` заменить `GoodsInTransitScan`:

```go
// GoodsInTransitScan records a single ТСД scan and echoes the product name.
func (ctrl *DocumentController) GoodsInTransitScan(c *gin.Context) {
	db, err := authorization.GetPgxPool(c)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.ErrorAndLog(err, "GoodsInTransitScan"))
		return
	}
	var req gitScanRequest
	if err := c.BindJSON(&req); err != nil {
		response.BadRequest(c, err)
		return
	}
	name, err := ctrl.documentService.ScanGoodsInTransit(c.Request.Context(), db, c.Param("id"), req.Barcode, req.BoxCode, req.Quantity)
	if err != nil {
		respondGITError(c, err)
		return
	}
	c.JSON(http.StatusOK, response.CorrectWithData(gin.H{
		"barcode":      req.Barcode,
		"product_name": name,
		"quantity":     req.Quantity,
	}))
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `go test ./documents/ -run 'TestGITProductByBarcodeReturnsName|TestGITScan|TestGITFinalize|TestGITCancel|TestGITCreate' -v`
Expected: PASS/SKIP, без compile-ошибок (существующие тесты вызывают `scanGoodsInTransitTx`, чья сигнатура не менялась).
Также: `go build ./...`
Expected: без ошибок.

- [ ] **Step 7: Commit**

```bash
git add documents/goods_in_transit_repo.go documents/goods_in_transit_service.go documents/goods_in_transit_controller.go documents/goods_in_transit_integration_test.go
git commit -m "feat(goods-in-transit): return scanned product name in scan response"
```

---

## Android (inventory)

> Сборка: из корня `inventory`. Windows: `.\gradlew.bat :app:assembleDebug`. WSL/Git Bash: `./gradlew :app:assembleDebug`.
> В Android-проекте нет unit-тестов на ViewModel — верификация каждой задачи: компиляция (`:app:compileDebugKotlin`), финальная — сборка APK + ручной прогон.

### Task 3: API-эндпоинты + DTO для goods-in-transit

**Files:**
- Modify: `app/src/main/java/io/proffi/inventory/network/ApiService.kt`

- [ ] **Step 1: Добавить эндпоинты в интерфейс ApiService**

В `ApiService.kt`, внутри `interface ApiService { ... }`, после блока Packing endpoint (перед закрывающей `}` интерфейса) добавить:

```kotlin
    // Goods-in-transit (товар в пути) endpoints
    @GET("api/v1/documents/goods-in-transit/")
    suspend fun getGoodsInTransit(
        @Query("warehouse_id") warehouseId: String,
        @Query("page") page: Int = 1
    ): GitListResponse

    @GET("api/v1/documents/goods-in-transit/{id}/")
    suspend fun getGoodsInTransitDetail(@Path("id") id: String): GitDetailResponse

    @POST("api/v1/documents/goods-in-transit/{id}/scan/")
    suspend fun scanGoodsInTransit(
        @Path("id") id: String,
        @Body request: GitScanRequest
    ): GitScanResponse

    @POST("api/v1/documents/goods-in-transit/{id}/finalize/")
    suspend fun finalizeGoodsInTransit(@Path("id") id: String): GitActionResponse

    @POST("api/v1/documents/goods-in-transit/{id}/cancel/")
    suspend fun cancelGoodsInTransit(@Path("id") id: String): GitActionResponse
```

- [ ] **Step 2: Добавить DTO-модели**

В конец `ApiService.kt` (после последней data class) добавить:

```kotlin
// Goods-in-transit models
data class GitListResponse(
    @SerializedName("page_count") val pageCount: Int,
    @SerializedName("total_items") val totalItems: Int,
    @SerializedName("item_per_page") val itemPerPage: Int,
    val body: List<GitDocument>? = null
)

data class GitDocument(
    val id: String,
    @SerializedName("document_number") val documentNumber: String,
    @SerializedName("external_document_number") val externalDocumentNumber: String,
    @SerializedName("warehouse_id") val warehouseId: String,
    val status: String,
    @SerializedName("line_count") val lineCount: Int,
    @SerializedName("ordered_total") val orderedTotal: Double,
    @SerializedName("received_total") val receivedTotal: Double
)

data class GitDetailResponse(
    val id: String,
    @SerializedName("external_document_number") val externalDocumentNumber: String,
    @SerializedName("warehouse_id") val warehouseId: String,
    val status: String,
    @SerializedName("receipt_document_base_id") val receiptDocumentBaseId: String?,
    val details: List<GitDetailLine>? = null,
    val scans: List<GitScanLine>? = null
)

data class GitDetailLine(
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    val barcode: String,
    val article: String,
    @SerializedName("ordered_quantity") val orderedQuantity: Double,
    @SerializedName("received_quantity") val receivedQuantity: Double,
    @SerializedName("invoice_price") val invoicePrice: Double,
    @SerializedName("sell_price") val sellPrice: Double
)

data class GitScanLine(
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("warehouse_box_id") val warehouseBoxId: String,
    val quantity: Double
)

data class GitScanRequest(
    val barcode: String,
    @SerializedName("box_code") val boxCode: String,
    val quantity: Double
)

data class GitScanResponse(
    val status: Int,
    val message: String,
    val body: GitScanBody?
)

data class GitScanBody(
    val barcode: String,
    @SerializedName("product_name") val productName: String?,
    val quantity: Double
)

data class GitActionResponse(
    val status: Int,
    val message: String
)
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/network/ApiService.kt
git commit -m "feat(git): add goods-in-transit API endpoints and DTOs"
```

---

### Task 4: GoodsInTransitRepository

**Files:**
- Create: `app/src/main/java/io/proffi/inventory/data/GoodsInTransitRepository.kt`

- [ ] **Step 1: Создать репозиторий**

```kotlin
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
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/data/GoodsInTransitRepository.kt
git commit -m "feat(git): add GoodsInTransitRepository"
```

---

### Task 5: GoodsInTransitViewModel

**Files:**
- Create: `app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitViewModel.kt`

- [ ] **Step 1: Создать ViewModel + sealed-состояния**

```kotlin
package io.proffi.inventory.ui.goodsintransit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.GoodsInTransitRepository
import io.proffi.inventory.network.GitDetailResponse
import io.proffi.inventory.network.GitDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GoodsInTransitViewModel(
    private val repository: GoodsInTransitRepository
) : ViewModel() {

    // ── Список документов ─────────────────────────────────────────────────────
    private val _listState = MutableStateFlow<GitListState>(GitListState.Loading)
    val listState: StateFlow<GitListState> = _listState

    fun loadDocuments(warehouseId: String) {
        viewModelScope.launch {
            _listState.value = GitListState.Loading
            val result = repository.getDocuments(warehouseId)
            _listState.value = if (result.isSuccess) {
                // Принимать можно только in_transit/receiving (бэкенд-фильтр поддерживает один статус)
                val docs = result.getOrNull()!!.filter {
                    it.status == STATUS_IN_TRANSIT || it.status == STATUS_RECEIVING
                }
                GitListState.Success(docs)
            } else {
                GitListState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    // ── Деталь документа ──────────────────────────────────────────────────────
    private val _detailState = MutableStateFlow<GitDetailState>(GitDetailState.Loading)
    val detailState: StateFlow<GitDetailState> = _detailState

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _detailState.value = GitDetailState.Loading
            val result = repository.getDetail(id)
            _detailState.value = if (result.isSuccess) {
                GitDetailState.Success(result.getOrNull()!!)
            } else {
                GitDetailState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    // ── Фаза скана: ячейка → товар ────────────────────────────────────────────
    private val _scanPhase = MutableStateFlow<GitScanPhase>(GitScanPhase.WaitingForCell)
    val scanPhase: StateFlow<GitScanPhase> = _scanPhase

    private val _scanFeedback = MutableStateFlow<GitScanFeedback>(GitScanFeedback.Idle)
    val scanFeedback: StateFlow<GitScanFeedback> = _scanFeedback

    fun resetPhase() {
        _scanPhase.value = GitScanPhase.WaitingForCell
    }

    /** Ячейка выбрана (скан или ручной ввод). */
    fun onCellSet(code: String) {
        val trimmed = code.trim()
        if (trimmed.isNotEmpty()) {
            _scanPhase.value = GitScanPhase.CellScanned(boxCode = trimmed)
            _scanFeedback.value = GitScanFeedback.Idle
        }
    }

    /** Скан товара в выбранную ячейку. */
    fun onProductScanned(documentId: String, barcode: String, quantity: Double) {
        val phase = _scanPhase.value as? GitScanPhase.CellScanned ?: return
        val detail = (_detailState.value as? GitDetailState.Success)?.detail ?: return
        val trimmed = barcode.trim()

        // Лёгкая клиентская проверка: товар должен быть в документе
        val line = detail.details?.find { it.barcode == trimmed }
        if (line == null) {
            _scanFeedback.value = GitScanFeedback.ProductNotFound
            return
        }

        viewModelScope.launch {
            _scanFeedback.value = GitScanFeedback.Loading
            val result = repository.scan(documentId, trimmed, phase.boxCode, quantity)
            if (result.isSuccess) {
                val name = result.getOrNull()?.body?.productName ?: line.productName
                _scanFeedback.value = GitScanFeedback.Success(name, quantity)
                // Обновить прогресс план/факт
                loadDetail(documentId)
            } else {
                _scanFeedback.value = GitScanFeedback.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun resetScanFeedback() {
        _scanFeedback.value = GitScanFeedback.Idle
    }

    // ── Финализация / отмена ──────────────────────────────────────────────────
    private val _actionState = MutableStateFlow<GitActionState>(GitActionState.Idle)
    val actionState: StateFlow<GitActionState> = _actionState

    fun finalize(documentId: String) {
        viewModelScope.launch {
            _actionState.value = GitActionState.Loading
            val result = repository.finalize(documentId)
            _actionState.value = if (result.isSuccess) GitActionState.FinalizeSuccess
            else GitActionState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    fun cancel(documentId: String) {
        viewModelScope.launch {
            _actionState.value = GitActionState.Loading
            val result = repository.cancel(documentId)
            _actionState.value = if (result.isSuccess) GitActionState.CancelSuccess
            else GitActionState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    fun resetActionState() {
        _actionState.value = GitActionState.Idle
    }

    companion object {
        const val STATUS_IN_TRANSIT = "in_transit"
        const val STATUS_RECEIVING = "receiving"
    }
}

sealed class GitListState {
    object Loading : GitListState()
    data class Success(val documents: List<GitDocument>) : GitListState()
    data class Error(val message: String) : GitListState()
}

sealed class GitDetailState {
    object Loading : GitDetailState()
    data class Success(val detail: GitDetailResponse) : GitDetailState()
    data class Error(val message: String) : GitDetailState()
}

sealed class GitScanPhase {
    object WaitingForCell : GitScanPhase()
    data class CellScanned(val boxCode: String) : GitScanPhase()
}

sealed class GitScanFeedback {
    object Idle : GitScanFeedback()
    object Loading : GitScanFeedback()
    data class Success(val productName: String, val quantity: Double) : GitScanFeedback()
    object ProductNotFound : GitScanFeedback()
    data class Error(val message: String) : GitScanFeedback()
}

sealed class GitActionState {
    object Idle : GitActionState()
    object Loading : GitActionState()
    object FinalizeSuccess : GitActionState()
    object CancelSuccess : GitActionState()
    data class Error(val message: String) : GitActionState()
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitViewModel.kt
git commit -m "feat(git): add GoodsInTransitViewModel with list/detail/scan/finalize states"
```

---

### Task 6: Строки RU/EN

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Добавить строки (RU) в `app/src/main/res/values/strings.xml`**

Перед закрывающим `</resources>` добавить:

```xml
    <!-- Goods-in-transit (приход товара в пути) -->
    <string name="menu_goods_in_transit">Приход в пути</string>
    <string name="git_title">Приход товара в пути</string>
    <string name="git_select_warehouse">Выберите склад</string>
    <string name="git_warehouse">Склад</string>
    <string name="git_select">Выбрать склад</string>
    <string name="git_view_documents">Показать документы</string>
    <string name="git_empty">Нет документов в пути</string>
    <string name="git_error_loading">Ошибка загрузки: %1$s</string>
    <string name="git_doc_number_label">Документ: %1$s</string>
    <string name="git_external_number_label">Внешний №: %1$s</string>
    <string name="git_progress_label">Принято %1$s из %2$s</string>
    <string name="git_status_in_transit">В пути</string>
    <string name="git_status_receiving">Приёмка</string>
    <string name="git_scan_cell_prompt">Отсканируйте ячейку</string>
    <string name="git_manual_cell_hint">Код ячейки</string>
    <string name="git_scan_product_prompt">Ячейка %1$s — сканируйте товар</string>
    <string name="git_change_cell">Сменить ячейку</string>
    <string name="git_manual_barcode_hint">Штрихкод товара</string>
    <string name="git_qty_hint">Кол-во</string>
    <string name="git_add">Добавить</string>
    <string name="git_product_not_found">Товар не в этом документе</string>
    <string name="git_scan_success">%1$s: +%2$s</string>
    <string name="git_ordered_label">Заказано</string>
    <string name="git_received_label">Принято</string>
    <string name="git_finalize">Завершить приёмку</string>
    <string name="git_cancel_doc">Отменить документ</string>
    <string name="git_finalize_confirm_title">Завершить приёмку?</string>
    <string name="git_finalize_confirm_message">Товар будет оприходован на склад и разложен по ячейкам. Расхождения попадут в акт.</string>
    <string name="git_finalize_done">Приёмка завершена</string>
    <string name="git_cancel_done">Документ отменён</string>
    <string name="git_confirm_yes">Да</string>
    <string name="git_confirm_no">Нет</string>
```

- [ ] **Step 2: Добавить строки (EN) в `app/src/main/res/values-en/strings.xml`**

Перед закрывающим `</resources>` добавить:

```xml
    <!-- Goods-in-transit -->
    <string name="menu_goods_in_transit">Goods in transit</string>
    <string name="git_title">Goods in transit receiving</string>
    <string name="git_select_warehouse">Select a warehouse</string>
    <string name="git_warehouse">Warehouse</string>
    <string name="git_select">Select warehouse</string>
    <string name="git_view_documents">Show documents</string>
    <string name="git_empty">No documents in transit</string>
    <string name="git_error_loading">Loading error: %1$s</string>
    <string name="git_doc_number_label">Document: %1$s</string>
    <string name="git_external_number_label">External no.: %1$s</string>
    <string name="git_progress_label">Received %1$s of %2$s</string>
    <string name="git_status_in_transit">In transit</string>
    <string name="git_status_receiving">Receiving</string>
    <string name="git_scan_cell_prompt">Scan a cell</string>
    <string name="git_manual_cell_hint">Cell code</string>
    <string name="git_scan_product_prompt">Cell %1$s — scan product</string>
    <string name="git_change_cell">Change cell</string>
    <string name="git_manual_barcode_hint">Product barcode</string>
    <string name="git_qty_hint">Qty</string>
    <string name="git_add">Add</string>
    <string name="git_product_not_found">Product is not in this document</string>
    <string name="git_scan_success">%1$s: +%2$s</string>
    <string name="git_ordered_label">Ordered</string>
    <string name="git_received_label">Received</string>
    <string name="git_finalize">Finish receiving</string>
    <string name="git_cancel_doc">Cancel document</string>
    <string name="git_finalize_confirm_title">Finish receiving?</string>
    <string name="git_finalize_confirm_message">Goods will be posted to stock and distributed into cells. Discrepancies go to the act.</string>
    <string name="git_finalize_done">Receiving finished</string>
    <string name="git_cancel_done">Document cancelled</string>
    <string name="git_confirm_yes">Yes</string>
    <string name="git_confirm_no">No</string>
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (ресурсы доступны как `R.string.git_*`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat(git): add goods-in-transit strings (RU/EN)"
```

---

### Task 7: Экран выбора склада + список документов

**Files:**
- Create: `app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitSelectionActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Создать Selection-Activity**

Переиспользуем `WarehouseSelectionDialog` из пакета productreceive (public @Composable).

```kotlin
package io.proffi.inventory.ui.goodsintransit

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.network.GitDocument
import io.proffi.inventory.network.Warehouse
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.ui.productreceive.WarehouseSelectionDialog
import io.proffi.inventory.ui.warehouse.WarehouseViewModel
import io.proffi.inventory.ui.warehouse.WarehousesState
import org.koin.androidx.viewmodel.ext.android.viewModel

class GoodsInTransitSelectionActivity : BaseActivity() {

    private val warehouseViewModel: WarehouseViewModel by viewModel()
    private val gitViewModel: GoodsInTransitViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GoodsInTransitSelectionScreen(
                    warehouseViewModel = warehouseViewModel,
                    gitViewModel = gitViewModel,
                    onBackPressed = { finish() },
                    onDocumentSelected = { doc ->
                        val intent = Intent(this, GoodsInTransitScannerActivity::class.java)
                        intent.putExtra("documentId", doc.id)
                        intent.putExtra("externalNumber", doc.externalDocumentNumber)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Освежить список при возврате со скан-экрана (документ мог быть завершён/отменён)
        (warehouseViewModel.warehousesState.value as? WarehousesState.Success)
        gitViewModel.selectedWarehouseId?.let { gitViewModel.loadDocuments(it) }
    }
}

@Composable
fun GoodsInTransitSelectionScreen(
    warehouseViewModel: WarehouseViewModel,
    gitViewModel: GoodsInTransitViewModel,
    onBackPressed: () -> Unit,
    onDocumentSelected: (GitDocument) -> Unit
) {
    val warehousesState by warehouseViewModel.warehousesState.collectAsState()
    val listState by gitViewModel.listState.collectAsState()

    var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var showWarehouseDialog by remember { mutableStateOf(false) }
    var showDocuments by remember { mutableStateOf(false) }

    if (showWarehouseDialog && warehousesState is WarehousesState.Success) {
        WarehouseSelectionDialog(
            warehouses = (warehousesState as WarehousesState.Success).warehouses,
            selectedWarehouse = selectedWarehouse,
            title = stringResource(R.string.git_select),
            onDismiss = { showWarehouseDialog = false },
            onSelect = { warehouse ->
                selectedWarehouse = warehouse
                showWarehouseDialog = false
                gitViewModel.selectedWarehouseId = warehouse.id
                gitViewModel.loadDocuments(warehouse.id)
                showDocuments = true
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val ws = warehousesState) {
                is WarehousesState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is WarehousesState.Error ->
                    Column(Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.warehouse_error, ws.message), color = MaterialTheme.colors.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { warehouseViewModel.loadWarehouses() }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                is WarehousesState.Success -> {
                    if (!showDocuments) {
                        WarehousePicker(
                            selectedName = selectedWarehouse?.name,
                            onPick = { showWarehouseDialog = true },
                            onContinue = {
                                selectedWarehouse?.let {
                                    gitViewModel.selectedWarehouseId = it.id
                                    gitViewModel.loadDocuments(it.id)
                                    showDocuments = true
                                }
                            }
                        )
                    } else {
                        DocumentsList(
                            warehouseName = selectedWarehouse?.name ?: "",
                            listState = listState,
                            onRetry = { selectedWarehouse?.let { gitViewModel.loadDocuments(it.id) } },
                            onDocumentSelected = onDocumentSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarehousePicker(
    selectedName: String?,
    onPick: () -> Unit,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.git_select_warehouse), style = MaterialTheme.typography.h6)
        Card(
            Modifier.fillMaxWidth().clickable { onPick() },
            elevation = 4.dp, shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warehouse, null, tint = MaterialTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.git_warehouse),
                        style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                }
                Spacer(Modifier.height(8.dp))
                Text(selectedName ?: stringResource(R.string.git_select),
                    style = MaterialTheme.typography.body1)
            }
        }
        if (selectedName != null) {
            Spacer(Modifier.weight(1f))
            Button(onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.git_view_documents))
            }
        }
    }
}

@Composable
private fun DocumentsList(
    warehouseName: String,
    listState: GitListState,
    onRetry: () -> Unit,
    onDocumentSelected: (GitDocument) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(16.dp), elevation = 4.dp) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.git_warehouse), style = MaterialTheme.typography.caption)
                Text(warehouseName, style = MaterialTheme.typography.h6)
            }
        }
        when (val s = listState) {
            is GitListState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is GitListState.Error ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.git_error_loading, s.message), color = MaterialTheme.colors.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRetry) { Text(stringResource(R.string.warehouse_retry)) }
                    }
                }
            is GitListState.Success -> {
                if (s.documents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LocalShipping, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.git_empty),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(s.documents, key = { it.id }) { doc ->
                            GitDocumentCard(doc = doc, onClick = { onDocumentSelected(doc) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitDocumentCard(doc: GitDocument, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = 4.dp, shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.LocalShipping, null,
                    tint = MaterialTheme.colors.primary, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.git_doc_number_label, doc.documentNumber),
                    style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.git_external_number_label, doc.externalDocumentNumber),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(2.dp))
                val statusLabel = if (doc.status == GoodsInTransitViewModel.STATUS_RECEIVING)
                    stringResource(R.string.git_status_receiving) else stringResource(R.string.git_status_in_transit)
                Text(statusLabel, style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.git_progress_label,
                    formatQty(doc.receivedTotal), formatQty(doc.orderedTotal)),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colors.primary)
        }
    }
}

/** "5" вместо "5.0", "2.5" сохраняем. */
fun formatQty(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
```

Также добавить хранение выбранного склада в ViewModel. В `GoodsInTransitViewModel.kt` добавить публичное поле сразу после объявления класса (рядом с `_listState`):

```kotlin
    /** Склад, выбранный на экране выбора (для refresh в onResume). */
    var selectedWarehouseId: String? = null
```

- [ ] **Step 2: Зарегистрировать Activity в манифесте**

В `app/src/main/AndroidManifest.xml`, после блока `ProductReceiveScannerActivity` (строки ~80-83), добавить:

```xml
        <activity
            android:name="io.proffi.inventory.ui.goodsintransit.GoodsInTransitSelectionActivity"
            android:exported="false"
            android:screenOrientation="portrait" />

        <activity
            android:name="io.proffi.inventory.ui.goodsintransit.GoodsInTransitScannerActivity"
            android:exported="false"
            android:screenOrientation="portrait" />
```

> `GoodsInTransitScannerActivity` создаётся в Task 8 — регистрируем сразу обе, чтобы не править манифест дважды.

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Ссылка на `GoodsInTransitScannerActivity` ещё не определена — она появится в Task 8; если компиляция падает на этой ссылке, выполнить Task 8 перед сборкой. Чтобы Task 7 компилировался автономно, сначала создать пустой каркас Activity из Task 8 Step 1.)

> **Порядок:** выполнить Task 8 Step 1 (каркас Scanner-Activity) до сборки Task 7, либо объединить сборку Task 7+8.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitSelectionActivity.kt app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitViewModel.kt app/src/main/AndroidManifest.xml
git commit -m "feat(git): warehouse selection + in-transit documents list screen"
```

---

### Task 8: Фазовый скан-экран (ячейка → товар → finalize)

**Files:**
- Create: `app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitScannerActivity.kt`

- [ ] **Step 1: Каркас Activity + интеграция сканера**

```kotlin
package io.proffi.inventory.ui.goodsintransit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.proffi.inventory.R
import io.proffi.inventory.network.GitDetailLine
import io.proffi.inventory.scanner.*
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.ScannerPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class GoodsInTransitScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: GoodsInTransitViewModel by viewModel()
    private var documentId: String = ""
    private var externalNumber: String = ""
    private lateinit var scannerManager: ScannerManager
    private lateinit var scannerPreferences: ScannerPreferences

    private var currentScannerType by mutableStateOf(ScannerType.CAMERA)

    /** Кол-во для следующего скана товара (камера-режим управляет полем). */
    private var pendingQuantity = 1.0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startBarcodeScanner()
        else Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    override fun onScanResult(barcode: String) {
        when (viewModel.scanPhase.value) {
            is GitScanPhase.WaitingForCell -> viewModel.onCellSet(barcode)
            is GitScanPhase.CellScanned -> viewModel.onProductScanned(documentId, barcode, pendingQuantity)
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documentId = intent.getStringExtra("documentId") ?: ""
        externalNumber = intent.getStringExtra("externalNumber") ?: ""

        scannerPreferences = ScannerPreferences(this)
        scannerManager = ScannerManager(this, this)

        lifecycleScope.launch {
            currentScannerType = scannerPreferences.scannerType.first()
            scannerManager.initScanner(currentScannerType)
            if (currentScannerType == ScannerType.UROVO_I6310) {
                scannerManager.getCurrentScanner()?.startScan()
            }
        }

        viewModel.resetPhase()
        viewModel.loadDetail(documentId)

        setContent {
            MaterialTheme {
                GoodsInTransitScannerScreen(
                    viewModel = viewModel,
                    documentId = documentId,
                    externalNumber = externalNumber,
                    currentScannerType = currentScannerType,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() },
                    onQuantityChange = { pendingQuantity = it },
                    onFinished = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentScannerType == ScannerType.UROVO_I6310) {
            scannerManager.getCurrentScanner()?.startScan()
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentScannerType == ScannerType.UROVO_I6310) {
            scannerManager.getCurrentScanner()?.stopScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerManager.release()
    }

    private fun checkCameraPermissionAndScan() {
        if (currentScannerType == ScannerType.CAMERA) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) startBarcodeScanner()
            else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startBarcodeScanner()
        }
    }

    private fun startBarcodeScanner() {
        scannerManager.getCurrentScanner()?.startScan()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (currentScannerType == ScannerType.CAMERA) {
            val barcode = CameraScanner.parseResult(requestCode, resultCode, data)
            if (barcode != null) onScanResult(barcode)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
```

- [ ] **Step 2: Compose-экран (фазы, прогресс, finalize)**

В тот же файл добавить:

```kotlin
@Composable
fun GoodsInTransitScannerScreen(
    viewModel: GoodsInTransitViewModel,
    documentId: String,
    externalNumber: String,
    currentScannerType: ScannerType,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit,
    onQuantityChange: (Double) -> Unit,
    onFinished: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()
    val scanPhase by viewModel.scanPhase.collectAsState()
    val scanFeedback by viewModel.scanFeedback.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val scaffoldState = rememberScaffoldState()

    var showFinalizeDialog by remember { mutableStateOf(false) }

    val msgNotFound = stringResource(R.string.git_product_not_found)
    val msgFinalizeDone = stringResource(R.string.git_finalize_done)
    val msgCancelDone = stringResource(R.string.git_cancel_done)

    LaunchedEffect(scanFeedback) {
        when (val f = scanFeedback) {
            is GitScanFeedback.ProductNotFound -> {
                scaffoldState.snackbarHostState.showSnackbar(msgNotFound)
                viewModel.resetScanFeedback()
            }
            is GitScanFeedback.Success -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    "${f.productName}: +${formatQty(f.quantity)}"
                )
                viewModel.resetScanFeedback()
            }
            is GitScanFeedback.Error -> {
                scaffoldState.snackbarHostState.showSnackbar(f.message)
                viewModel.resetScanFeedback()
            }
            else -> {}
        }
    }

    LaunchedEffect(actionState) {
        when (actionState) {
            is GitActionState.FinalizeSuccess -> {
                scaffoldState.snackbarHostState.showSnackbar(msgFinalizeDone)
                viewModel.resetActionState()
                onFinished()
            }
            is GitActionState.CancelSuccess -> {
                scaffoldState.snackbarHostState.showSnackbar(msgCancelDone)
                viewModel.resetActionState()
                onFinished()
            }
            is GitActionState.Error -> {
                scaffoldState.snackbarHostState.showSnackbar((actionState as GitActionState.Error).message)
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    if (showFinalizeDialog) {
        AlertDialog(
            onDismissRequest = { showFinalizeDialog = false },
            title = { Text(stringResource(R.string.git_finalize_confirm_title)) },
            text = { Text(stringResource(R.string.git_finalize_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    showFinalizeDialog = false
                    viewModel.finalize(documentId)
                }) { Text(stringResource(R.string.git_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinalizeDialog = false }) {
                    Text(stringResource(R.string.git_confirm_no))
                }
            }
        )
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(externalNumber.ifEmpty { stringResource(R.string.git_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.cancel(documentId) }) {
                        Text(stringResource(R.string.git_cancel_doc), color = MaterialTheme.colors.error)
                    }
                }
            )
        },
        bottomBar = {
            Surface(elevation = 8.dp) {
                Button(
                    onClick = { showFinalizeDialog = true },
                    enabled = actionState !is GitActionState.Loading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.git_finalize))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Фаза ──────────────────────────────────────────────────────────
            when (val phase = scanPhase) {
                is GitScanPhase.WaitingForCell -> CellScanBanner(
                    isCameraMode = currentScannerType == ScannerType.CAMERA,
                    onScanClick = onScanClick,
                    onCellConfirmed = { viewModel.onCellSet(it) }
                )
                is GitScanPhase.CellScanned -> ProductScanBanner(
                    boxCode = phase.boxCode,
                    isCameraMode = currentScannerType == ScannerType.CAMERA,
                    onScanClick = onScanClick,
                    onChangeCell = { viewModel.resetPhase() },
                    onProductConfirmed = { barcode, qty ->
                        onQuantityChange(qty)
                        viewModel.onProductScanned(documentId, barcode, qty)
                    }
                )
            }

            if (scanFeedback is GitScanFeedback.Loading || actionState is GitActionState.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // ── Прогресс план/факт ────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = detailState) {
                    is GitDetailState.Loading ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is GitDetailState.Error ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(s.message, color = MaterialTheme.colors.error)
                        }
                    is GitDetailState.Success -> {
                        val lines = s.detail.details.orEmpty()
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(lines, key = { it.productId }) { line ->
                                GitLineCard(line)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellScanBanner(
    isCameraMode: Boolean,
    onScanClick: () -> Unit,
    onCellConfirmed: (String) -> Unit
) {
    var manual by remember { mutableStateOf("") }
    Column {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colors.primary, elevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.git_scan_cell_prompt), color = Color.White,
                    style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (isCameraMode) {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan), tint = Color.White)
                    }
                }
            }
        }
        if (isCameraMode) {
            Surface(elevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it },
                        label = { Text(stringResource(R.string.git_manual_cell_hint)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (manual.isNotBlank()) { onCellConfirmed(manual); manual = "" }
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (manual.isNotBlank()) { onCellConfirmed(manual); manual = "" } },
                        enabled = manual.isNotBlank(), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductScanBanner(
    boxCode: String,
    isCameraMode: Boolean,
    onScanClick: () -> Unit,
    onChangeCell: () -> Unit,
    onProductConfirmed: (String, Double) -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    Column {
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF388E3C), elevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.git_scan_product_prompt, boxCode), color = Color.White,
                    style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (isCameraMode) {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan), tint = Color.White)
                    }
                }
                TextButton(onClick = onChangeCell) {
                    Text(stringResource(R.string.git_change_cell), color = Color.White,
                        style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (isCameraMode) {
            Surface(elevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode, onValueChange = { barcode = it },
                        label = { Text(stringResource(R.string.git_manual_barcode_hint)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = qty, onValueChange = { qty = it },
                        label = { Text(stringResource(R.string.git_qty_hint)) },
                        singleLine = true, modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val q = qty.toDoubleOrNull() ?: 1.0
                            if (barcode.isNotBlank() && q > 0) { onProductConfirmed(barcode.trim(), q); barcode = ""; qty = "1" }
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val q = qty.toDoubleOrNull() ?: 1.0
                        if (barcode.isNotBlank() && q > 0) { onProductConfirmed(barcode.trim(), q); barcode = ""; qty = "1" }
                    }, enabled = barcode.isNotBlank(), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.git_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun GitLineCard(line: GitDetailLine) {
    val done = line.receivedQuantity >= line.orderedQuantity && line.orderedQuantity > 0
    Card(
        Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(10.dp),
        backgroundColor = if (done) Color(0xFFE8F5E9) else MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.productName, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.item_article_label, line.article),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text(stringResource(R.string.item_barcode_label, line.barcode),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                if (done) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${stringResource(R.string.git_received_label)}: ${formatQty(line.receivedQuantity)}",
                    style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold,
                    color = if (done) Color(0xFF4CAF50) else MaterialTheme.colors.primary)
                Text("${stringResource(R.string.git_ordered_label)}: ${formatQty(line.orderedQuantity)}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(6.dp))
            val progress = if (line.orderedQuantity > 0)
                (line.receivedQuantity / line.orderedQuantity).toFloat().coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = progress, modifier = Modifier.fillMaxWidth(),
                color = if (done) Color(0xFF4CAF50) else MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}
```

> Использует существующие строки `item_article_label`, `item_barcode_label`, `scan`, `cd_back`, `camera_permission_required` (есть в проекте — из packing/assembly).

- [ ] **Step 3: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/ui/goodsintransit/GoodsInTransitScannerActivity.kt
git commit -m "feat(git): phase-based scan screen (cell then product) with finalize/cancel"
```

---

### Task 9: DI-регистрация (Koin)

**Files:**
- Modify: `app/src/main/java/io/proffi/inventory/di/AppModule.kt`

- [ ] **Step 1: Зарегистрировать репозиторий и ViewModel**

В `AppModule.kt`:

1. Добавить импорт рядом с прочими ui-импортами:

```kotlin
import io.proffi.inventory.ui.goodsintransit.GoodsInTransitViewModel
```

2. В блок `// Repositories` добавить:

```kotlin
    single { GoodsInTransitRepository(get()) }
```

(импорт репозитория покрыт существующим `import io.proffi.inventory.data.*`)

3. В блок `// ViewModels` добавить:

```kotlin
    viewModelOf(::GoodsInTransitViewModel)
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/di/AppModule.kt
git commit -m "feat(git): register GoodsInTransit repository and view model in Koin"
```

---

### Task 10: Пункт меню в MainActivity + финальная сборка

**Files:**
- Modify: `app/src/main/java/io/proffi/inventory/ui/main/MainActivity.kt`

- [ ] **Step 1: Добавить колбэк навигации**

В `MainActivity.onCreate`, в вызов `MainScreen(...)`, после `onPackingClick = { ... }` добавить:

```kotlin
                    onGoodsInTransitClick = {
                        startActivity(Intent(activity, io.proffi.inventory.ui.goodsintransit.GoodsInTransitSelectionActivity::class.java))
                    },
```

- [ ] **Step 2: Прокинуть параметр через MainScreen и DrawerContent**

В сигнатуру `fun MainScreen(...)` добавить параметр (после `onPackingClick: () -> Unit,`):

```kotlin
    onGoodsInTransitClick: () -> Unit,
```

В вызове `DrawerContent(...)` внутри `MainScreen` добавить (после блока `onPackingClick = { ... }`):

```kotlin
                onGoodsInTransitClick = {
                    scope.launch { scaffoldState.drawerState.close() }
                    onGoodsInTransitClick()
                },
```

В сигнатуру `fun DrawerContent(...)` добавить параметр (после `onPackingClick: () -> Unit,`):

```kotlin
    onGoodsInTransitClick: () -> Unit,
```

В тело `DrawerContent`, после пункта «Упаковка товара» (`DrawerMenuItem(... onPackingClick)`), добавить:

```kotlin
        // Пункт меню: Приход в пути
        DrawerMenuItem(
            icon = Icons.Default.LocalShipping,
            text = stringResource(R.string.menu_goods_in_transit),
            onClick = onGoodsInTransitClick
        )
```

- [ ] **Step 3: Финальная сборка APK**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL; APK в `app/build/outputs/apk/debug/`.

- [ ] **Step 4: Ручная проверка на устройстве/эмуляторе**

1. Войти, открыть drawer → «Приход в пути».
2. Выбрать склад с документом в статусе in_transit/receiving (создать док из Excel на web заранее).
3. Открыть документ → отсканировать ячейку → отсканировать товар(ы) → прогресс «принято» растёт, snackbar с именем товара.
4. «Завершить приёмку» → подтвердить → возврат к списку, документ исчезает (стал completed).
5. Проверить на web/БД: сток вырос, товар разложен в ячейку, при расхождении создан акт.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/proffi/inventory/ui/main/MainActivity.kt
git commit -m "feat(git): add goods-in-transit entry to main drawer menu"
```

---

## Self-Review (выполнено автором плана)

**Spec coverage:**
- Список доков (склад + фильтр in_transit/receiving) → Task 5 (`loadDocuments` + фильтр), Task 7.
- Приём+раскладка (скан ячейки→товара) → Task 5 (фазы), Task 8.
- Имена товаров → Task 1 (detail), Task 2 (scan).
- Finalize / Cancel → Task 5, Task 8.
- Пункт меню → Task 10.
- Строки RU/EN → Task 6.
- Краевые случаи (не в доке / не-receiving finalize / неизвестный штрихкод) → клиентская проверка Task 5 + серверные ошибки через snackbar Task 8.
- Тесты backend → Task 1, Task 2.

**Type consistency:** Имена статусов через `GoodsInTransitViewModel.STATUS_IN_TRANSIT/STATUS_RECEIVING`. `formatQty` объявлена один раз (Task 7) и переиспользуется в Task 8. DTO-имена (`GitDocument`, `GitDetailLine`, `GitScanBody` …) совпадают между ApiService, Repository, ViewModel, экранами. Backend `GITProductByBarcode` 3-значная сигнатура согласована между repo/service/test.

**Зависимость сборки:** Task 7 ссылается на `GoodsInTransitScannerActivity` (Task 8). Для автономной компиляции выполнять Task 8 (Step 1 каркас) перед сборкой Task 7, либо собирать Task 7+8 вместе.

**Placeholder scan:** плейсхолдеров нет — весь код приведён.

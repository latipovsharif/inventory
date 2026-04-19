package io.proffi.inventory.ui.assembly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.AssemblyRepository
import io.proffi.inventory.network.Recommendation
import io.proffi.inventory.network.RecommendationDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AssemblyViewModel(
    private val assemblyRepository: AssemblyRepository
) : ViewModel() {

    // ── Recommendations list ──────────────────────────────────────────────────
    private val _recommendationsState =
        MutableStateFlow<RecommendationsState>(RecommendationsState.Loading)
    val recommendationsState: StateFlow<RecommendationsState> = _recommendationsState

    private val loadedItems = mutableListOf<Recommendation>()
    private var currentPage = 0
    private var totalItems = 0
    private var isLoadingMore = false

    fun loadRecommendations() {
        viewModelScope.launch {
            loadedItems.clear()
            currentPage = 0
            totalItems = 0
            isLoadingMore = false
            _recommendationsState.value = RecommendationsState.Loading
            fetchPage(1)
        }
    }

    fun loadNextPage() {
        val state = _recommendationsState.value as? RecommendationsState.Success ?: return
        if (!state.hasMore || isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            _recommendationsState.value = RecommendationsState.LoadingMore(state.items)
            fetchPage(currentPage + 1)
            isLoadingMore = false
        }
    }

    private suspend fun fetchPage(page: Int) {
        val result = assemblyRepository.getRecommendations(page, status = "collecting")
        if (result.isSuccess) {
            val response = result.getOrNull()!!
            totalItems = response.totalItems
            currentPage = page
            loadedItems.addAll(response.body.orEmpty())
            val hasMore = loadedItems.size < totalItems
            _recommendationsState.value = RecommendationsState.Success(loadedItems.toList(), hasMore)
        } else {
            _recommendationsState.value = RecommendationsState.Error(
                result.exceptionOrNull()?.message ?: "Unknown error"
            )
        }
    }

    // ── Recommendation detail ─────────────────────────────────────────────────
    private val _detailState = MutableStateFlow<DetailState>(DetailState.Loading)
    val detailState: StateFlow<DetailState> = _detailState

    fun loadRecommendationDetail(id: String) {
        viewModelScope.launch {
            _detailState.value = DetailState.Loading
            val result = assemblyRepository.getRecommendationDetail(id)
            _detailState.value = if (result.isSuccess) {
                DetailState.Success(result.getOrNull()!!)
            } else {
                DetailState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    // ── Scanner ───────────────────────────────────────────────────────────────
    private val _scanPhase = MutableStateFlow<ScanPhase>(ScanPhase.WaitingForBox)
    val scanPhase: StateFlow<ScanPhase> = _scanPhase

    private val _collectState = MutableStateFlow<CollectState>(CollectState.Idle)
    val collectState: StateFlow<CollectState> = _collectState

    private val _lastScannedProductId = MutableStateFlow<String?>(null)
    val lastScannedProductId: StateFlow<String?> = _lastScannedProductId

    fun resetScanPhase() {
        _scanPhase.value = ScanPhase.WaitingForBox
    }

    /** Called for EVERY scan in WaitingForBox phase – and also when a new box is scanned
     *  while already in BoxScanned phase (auto-switch). */
    fun onBoxScanned(barcode: String) {
        val detail = (_detailState.value as? DetailState.Success)?.detail ?: return
        val trimmed = barcode.trim()
        for (item in detail.details) {
            for (location in item.locations) {
                val box = location.box
                if (box.code.equals(trimmed, ignoreCase = true) ||
                    box.id.equals(trimmed, ignoreCase = true)
                ) {
                    val label = "${location.zone.code}→${location.shelf.code}→${location.box.code}"
                    _scanPhase.value = ScanPhase.BoxScanned(
                        boxId = box.id,
                        boxCode = box.code,
                        locationLabel = label
                    )
                    _collectState.value = CollectState.Idle
                    return
                }
            }
        }
        _collectState.value = CollectState.BoxNotFound
    }

    fun onProductScanned(barcode: String) {
        val phase = _scanPhase.value as? ScanPhase.BoxScanned ?: return
        val detail = (_detailState.value as? DetailState.Success)?.detail ?: return
        val detailItem = detail.details.find { it.product.barcode == barcode }
        if (detailItem == null) {
            _collectState.value = CollectState.ProductNotFound
            return
        }
        // Check if the product is already fully collected → excess scan
        val alreadyCollected = detailItem.collectedQuantity ?: 0
        if (alreadyCollected >= detailItem.requestedQuantity) {
            _collectState.value = CollectState.ExcessProduct(detailItem.product.name)
            return
        }
        viewModelScope.launch {
            _collectState.value = CollectState.Loading
            val result = assemblyRepository.collectProduct(
                recommendationId = detail.id,
                boxId = phase.boxId,
                productId = detailItem.product.id,
                quantity = 1.0
            )
            if (result.isSuccess) {
                val updated = result.getOrNull()!!
                _lastScannedProductId.value = detailItem.product.id
                _detailState.value = DetailState.Success(updated)
                _collectState.value = CollectState.Success(updated)
            } else {
                _collectState.value = CollectState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun resetCollectState() {
        _collectState.value = CollectState.Idle
    }
}

// ── Sealed classes ────────────────────────────────────────────────────────────

sealed class RecommendationsState {
    object Loading : RecommendationsState()
    data class LoadingMore(val items: List<Recommendation>) : RecommendationsState()
    data class Success(val items: List<Recommendation>, val hasMore: Boolean) : RecommendationsState()
    data class Error(val message: String) : RecommendationsState()
}

sealed class DetailState {
    object Loading : DetailState()
    data class Success(val detail: RecommendationDetail) : DetailState()
    data class Error(val message: String) : DetailState()
}

sealed class ScanPhase {
    object WaitingForBox : ScanPhase()
    data class BoxScanned(
        val boxId: String,
        val boxCode: String,
        val locationLabel: String
    ) : ScanPhase()
}

sealed class CollectState {
    object Idle : CollectState()
    object Loading : CollectState()
    object BoxNotFound : CollectState()
    object ProductNotFound : CollectState()
    data class ExcessProduct(val productName: String) : CollectState()
    data class Success(val updatedDetail: RecommendationDetail) : CollectState()
    data class Error(val message: String) : CollectState()
}


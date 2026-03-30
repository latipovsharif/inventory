package io.proffi.inventory.ui.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.PackingRepository
import io.proffi.inventory.network.Recommendation
import io.proffi.inventory.network.RecommendationDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PackingViewModel(
    private val packingRepository: PackingRepository
) : ViewModel() {

    // ── Recommendations list (filtered by packing statuses) ───────────────────
    private val _listState = MutableStateFlow<PackingListState>(PackingListState.Loading)
    val listState: StateFlow<PackingListState> = _listState

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
            _listState.value = PackingListState.Loading
            fetchPage(1)
        }
    }

    fun loadNextPage() {
        val state = _listState.value as? PackingListState.Success ?: return
        if (!state.hasMore || isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            _listState.value = PackingListState.LoadingMore(state.items)
            fetchPage(currentPage + 1)
            isLoadingMore = false
        }
    }

    private suspend fun fetchPage(page: Int) {
        // Fetch recommendations with status collecting OR packaging
        val result = packingRepository.getPackingRecommendations(page, "packaging")
        if (result.isSuccess) {
            val response = result.getOrNull()!!
            totalItems = response.totalItems
            currentPage = page
            loadedItems.addAll(response.body)
            val hasMore = loadedItems.size < totalItems
            _listState.value = PackingListState.Success(loadedItems.toList(), hasMore)
        } else {
            _listState.value = PackingListState.Error(
                result.exceptionOrNull()?.message ?: "Unknown error"
            )
        }
    }

    // ── Recommendation detail ─────────────────────────────────────────────────
    private val _detailState = MutableStateFlow<PackingDetailState>(PackingDetailState.Loading)
    val detailState: StateFlow<PackingDetailState> = _detailState

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _detailState.value = PackingDetailState.Loading
            val result = packingRepository.getRecommendationDetail(id)
            _detailState.value = if (result.isSuccess) {
                PackingDetailState.Success(result.getOrNull()!!)
            } else {
                PackingDetailState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    // ── Scan phases ───────────────────────────────────────────────────────────
    private val _packPhase = MutableStateFlow<PackPhase>(PackPhase.WaitingForBox)
    val packPhase: StateFlow<PackPhase> = _packPhase

    private val _packState = MutableStateFlow<PackState>(PackState.Idle)
    val packState: StateFlow<PackState> = _packState

    fun resetPackPhase() {
        _packPhase.value = PackPhase.WaitingForBox
    }

    /** Called when a box barcode/code is set (via scan or manual entry). */
    fun onBoxCodeSet(code: String) {
        val trimmed = code.trim()
        if (trimmed.isNotEmpty()) {
            _packPhase.value = PackPhase.BoxScanned(boxCode = trimmed)
            _packState.value = PackState.Idle
        }
    }

    /** Called when a product barcode is scanned in BoxScanned phase. */
    fun onProductScanned(barcode: String) {
        val phase = _packPhase.value as? PackPhase.BoxScanned ?: return
        val detail = (_detailState.value as? PackingDetailState.Success)?.detail ?: return
        val trimmed = barcode.trim()

        viewModelScope.launch {
            _packState.value = PackState.Loading
            val result = packingRepository.packProduct(
                recommendationId = detail.id,
                boxCode = phase.boxCode,
                barcode = trimmed,
                quantity = 1
            )
            if (result.isSuccess) {
                val updated = result.getOrNull()!!
                _detailState.value = PackingDetailState.Success(updated)
                _packState.value = PackState.Success
            } else {
                _packState.value = PackState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }

    fun resetPackState() {
        _packState.value = PackState.Idle
    }
}

// ── Sealed classes ────────────────────────────────────────────────────────────

sealed class PackingListState {
    object Loading : PackingListState()
    data class LoadingMore(val items: List<Recommendation>) : PackingListState()
    data class Success(val items: List<Recommendation>, val hasMore: Boolean) : PackingListState()
    data class Error(val message: String) : PackingListState()
}

sealed class PackingDetailState {
    object Loading : PackingDetailState()
    data class Success(val detail: RecommendationDetail) : PackingDetailState()
    data class Error(val message: String) : PackingDetailState()
}

sealed class PackPhase {
    object WaitingForBox : PackPhase()
    data class BoxScanned(val boxCode: String) : PackPhase()
}

sealed class PackState {
    object Idle : PackState()
    object Loading : PackState()
    object Success : PackState()
    data class Error(val message: String) : PackState()
}

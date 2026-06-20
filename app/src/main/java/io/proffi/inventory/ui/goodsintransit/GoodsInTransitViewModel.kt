package io.proffi.inventory.ui.goodsintransit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.GoodsInTransitRepository
import io.proffi.inventory.network.GitDetailResponse
import io.proffi.inventory.network.GitDocument
import io.proffi.inventory.util.ScanDebouncer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GoodsInTransitViewModel(
    private val repository: GoodsInTransitRepository
) : ViewModel() {

    // ── Список документов ─────────────────────────────────────────────────────
    private val _listState = MutableStateFlow<GitListState>(GitListState.Loading)
    val listState: StateFlow<GitListState> = _listState

    /** Склад, выбранный на экране выбора (для refresh в onResume). */
    var selectedWarehouseId: String? = null

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

    private val scanDebouncer = ScanDebouncer()

    fun resetPhase() {
        _scanPhase.value = GitScanPhase.WaitingForCell
        scanDebouncer.reset()
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
        // Ignore scans while a scan request is in flight, and drop duplicate
        // hardware fires of the same barcode (prevents double-count).
        if (_scanFeedback.value is GitScanFeedback.Loading) return
        val trimmed = barcode.trim()
        if (scanDebouncer.isDuplicate(trimmed)) return

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
                val name = result.getOrNull()?.body?.productName ?: line.productName ?: trimmed
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

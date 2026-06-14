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

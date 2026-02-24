package io.proffi.inventory.ui.productreceive

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
import io.proffi.inventory.network.ProductMove
import io.proffi.inventory.network.Warehouse
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.ui.warehouse.WarehouseViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.*

class ProductReceiveSelectionActivity : BaseActivity() {

    private val warehouseViewModel: WarehouseViewModel by viewModel()
    private val productReceiveViewModel: ProductReceiveViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ProductReceiveSelectionScreen(
                    warehouseViewModel = warehouseViewModel,
                    productReceiveViewModel = productReceiveViewModel,
                    onBackPressed = { finish() },
                    onMoveSelected = { move ->
                        val intent = android.content.Intent(this@ProductReceiveSelectionActivity, ProductReceiveScannerActivity::class.java)
                        intent.putExtra("moveId", move.id)
                        intent.putExtra("fromWarehouseName", move.warehouseFrom.name)
                        intent.putExtra("toWarehouseName", move.warehouseTo.name)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun ProductReceiveSelectionScreen(
    warehouseViewModel: WarehouseViewModel,
    productReceiveViewModel: ProductReceiveViewModel,
    onBackPressed: () -> Unit,
    onMoveSelected: (ProductMove) -> Unit
) {
    val warehousesState by warehouseViewModel.warehousesState.collectAsState()
    val incomingMovesState by productReceiveViewModel.incomingMovesState.collectAsState()

    var selectedWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var showWarehouseDialog by remember { mutableStateOf(false) }
    var showIncomingMoves by remember { mutableStateOf(false) }

    if (showWarehouseDialog && warehousesState is io.proffi.inventory.ui.warehouse.WarehousesState.Success) {
        WarehouseSelectionDialog(
            warehouses = (warehousesState as io.proffi.inventory.ui.warehouse.WarehousesState.Success).warehouses,
            selectedWarehouse = selectedWarehouse,
            title = stringResource(R.string.product_receive_select),
            onDismiss = { showWarehouseDialog = false },
            onSelect = { warehouse ->
                selectedWarehouse = warehouse
                showWarehouseDialog = false
                // Загружаем входящие перемещения для выбранного склада
                productReceiveViewModel.loadIncomingProductMoves(warehouse.id)
                showIncomingMoves = true
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_receive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = warehousesState) {
                is io.proffi.inventory.ui.warehouse.WarehousesState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is io.proffi.inventory.ui.warehouse.WarehousesState.Success -> {
                    if (!showIncomingMoves) {
                        // Экран выбора склада
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.product_receive_select_warehouse),
                                style = MaterialTheme.typography.h6
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showWarehouseDialog = true },
                                elevation = 4.dp,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warehouse,
                                            contentDescription = null,
                                            tint = MaterialTheme.colors.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.product_receive_warehouse),
                                            style = MaterialTheme.typography.subtitle2,
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = selectedWarehouse?.name ?: stringResource(R.string.product_receive_select),
                                        style = MaterialTheme.typography.body1
                                    )
                                }
                            }

                            if (selectedWarehouse != null) {
                                Spacer(modifier = Modifier.weight(1f))

                                Button(
                                    onClick = {
                                        productReceiveViewModel.loadIncomingProductMoves(selectedWarehouse!!.id)
                                        showIncomingMoves = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.product_receive_view_incoming))
                                }
                            }
                        }
                    } else {
                        // Экран списка входящих перемещений
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Шапка с информацией о складе
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                elevation = 4.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.product_receive_warehouse),
                                        style = MaterialTheme.typography.caption
                                    )
                                    Text(
                                        text = selectedWarehouse?.name ?: "",
                                        style = MaterialTheme.typography.h6
                                    )
                                }
                            }

                            when (val movesState = incomingMovesState) {
                                is IncomingMovesState.Loading -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                is IncomingMovesState.Success -> {
                                    if (movesState.moves.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Inbox,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(64.dp),
                                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = stringResource(R.string.product_receive_empty),
                                                    style = MaterialTheme.typography.body1,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(movesState.moves) { move ->
                                                IncomingMoveCard(
                                                    move = move,
                                                    onClick = { onMoveSelected(move) }
                                                )
                                            }
                                        }
                                    }
                                }
                                is IncomingMovesState.Error -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = stringResource(R.string.product_receive_error_loading, movesState.message),
                                                color = MaterialTheme.colors.error
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = {
                                                selectedWarehouse?.let {
                                                    productReceiveViewModel.loadIncomingProductMoves(it.id)
                                                }
                                            }) {
                                                Text(stringResource(R.string.warehouse_retry))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is io.proffi.inventory.ui.warehouse.WarehousesState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.warehouse_error, state.message),
                            color = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { warehouseViewModel.loadWarehouses() }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingMoveCard(
    move: ProductMove,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${move.warehouseFrom.name} → ${move.warehouseTo.name}",
                    style = MaterialTheme.typography.subtitle1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.product_receive_document_number_label, move.documentNumber),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.product_receive_total_items_label, move.totalItems),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(move.createdAt),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colors.primary
            )
        }
    }
}

@Composable
fun WarehouseSelectionDialog(
    warehouses: List<Warehouse>,
    selectedWarehouse: Warehouse?,
    title: String,
    onDismiss: () -> Unit,
    onSelect: (Warehouse) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(warehouses) { warehouse ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(warehouse) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = warehouse.id == selectedWarehouse?.id,
                            onClick = { onSelect(warehouse) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = warehouse.name,
                                style = MaterialTheme.typography.body1
                            )
                            Text(
                                text = warehouse.store.name,
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.product_move_conflict_ok))
            }
        }
    )
}

private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

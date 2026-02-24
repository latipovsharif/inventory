package io.proffi.inventory.ui.productmove

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
import io.proffi.inventory.network.Warehouse
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.ui.warehouse.WarehouseViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProductMoveSelectionActivity : BaseActivity() {

    private val warehouseViewModel: WarehouseViewModel by viewModel()
    private val productMoveViewModel: ProductMoveViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ProductMoveSelectionScreen(
                    warehouseViewModel = warehouseViewModel,
                    productMoveViewModel = productMoveViewModel,
                    onBackPressed = { finish() },
                    onMoveStarted = { moveId, fromWarehouse, toWarehouse ->
                        val intent = android.content.Intent(this@ProductMoveSelectionActivity, ProductMoveScannerActivity::class.java)
                        intent.putExtra("moveId", moveId)
                        intent.putExtra("fromWarehouseName", fromWarehouse.name)
                        intent.putExtra("toWarehouseName", toWarehouse.name)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun ProductMoveSelectionScreen(
    warehouseViewModel: WarehouseViewModel,
    productMoveViewModel: ProductMoveViewModel,
    onBackPressed: () -> Unit,
    onMoveStarted: (String, Warehouse, Warehouse) -> Unit
) {
    val warehousesState by warehouseViewModel.warehousesState.collectAsState()
    val startMoveState by productMoveViewModel.startMoveState.collectAsState()

    var selectedFromWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var selectedToWarehouse by remember { mutableStateOf<Warehouse?>(null) }
    var showFromDialog by remember { mutableStateOf(false) }
    var showToDialog by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }

    LaunchedEffect(startMoveState) {
        when (startMoveState) {
            is StartMoveState.Success -> {
                val moveId = (startMoveState as StartMoveState.Success).moveId
                if (selectedFromWarehouse != null && selectedToWarehouse != null) {
                    onMoveStarted(moveId, selectedFromWarehouse!!, selectedToWarehouse!!)
                    productMoveViewModel.resetStartMoveState()
                }
            }
            is StartMoveState.Conflict -> {
                conflictMessage = (startMoveState as StartMoveState.Conflict).message
                showConflictDialog = true
                productMoveViewModel.resetStartMoveState()
            }
            is StartMoveState.Error -> {
                conflictMessage = (startMoveState as StartMoveState.Error).message
                showConflictDialog = true
                productMoveViewModel.resetStartMoveState()
            }
            else -> {}
        }
    }

    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text(stringResource(R.string.product_move_conflict_dialog_title)) },
            text = { Text(conflictMessage) },
            confirmButton = {
                TextButton(onClick = { showConflictDialog = false }) {
                    Text(stringResource(R.string.product_move_conflict_ok))
                }
            }
        )
    }

    if (showFromDialog && warehousesState is io.proffi.inventory.ui.warehouse.WarehousesState.Success) {
        WarehouseSelectionDialog(
            warehouses = (warehousesState as io.proffi.inventory.ui.warehouse.WarehousesState.Success).warehouses,
            selectedWarehouse = selectedFromWarehouse,
            title = stringResource(R.string.product_move_select_from),
            onDismiss = { showFromDialog = false },
            onSelect = { warehouse ->
                selectedFromWarehouse = warehouse
                showFromDialog = false
            }
        )
    }

    if (showToDialog && warehousesState is io.proffi.inventory.ui.warehouse.WarehousesState.Success) {
        WarehouseSelectionDialog(
            warehouses = (warehousesState as io.proffi.inventory.ui.warehouse.WarehousesState.Success).warehouses,
            selectedWarehouse = selectedToWarehouse,
            title = stringResource(R.string.product_move_select_to),
            onDismiss = { showToDialog = false },
            onSelect = { warehouse ->
                selectedToWarehouse = warehouse
                showToDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_move_title)) },
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.product_move_select_warehouses),
                            style = MaterialTheme.typography.h6
                        )

                        // From Warehouse Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFromDialog = true },
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
                                        text = stringResource(R.string.product_move_from_warehouse),
                                        style = MaterialTheme.typography.subtitle2,
                                        color = MaterialTheme.colors.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = selectedFromWarehouse?.name ?: stringResource(R.string.product_move_select_from),
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }

                        // Arrow Icon
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(32.dp),
                            tint = MaterialTheme.colors.primary
                        )

                        // To Warehouse Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showToDialog = true },
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
                                        tint = MaterialTheme.colors.secondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.product_move_to_warehouse),
                                        style = MaterialTheme.typography.subtitle2,
                                        color = MaterialTheme.colors.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = selectedToWarehouse?.name ?: stringResource(R.string.product_move_select_to),
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Start Move Button
                        Button(
                            onClick = {
                                if (selectedFromWarehouse != null && selectedToWarehouse != null) {
                                    if (selectedFromWarehouse!!.id == selectedToWarehouse!!.id) {
                                        conflictMessage = "Склады отправления и назначения не могут быть одинаковыми"
                                        showConflictDialog = true
                                    } else {
                                        productMoveViewModel.startProductMove(
                                            selectedFromWarehouse!!.id,
                                            selectedToWarehouse!!.id
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = selectedFromWarehouse != null &&
                                     selectedToWarehouse != null &&
                                     startMoveState !is StartMoveState.Loading,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (startMoveState is StartMoveState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colors.onPrimary
                                )
                            } else {
                                Text(stringResource(R.string.product_move_start))
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

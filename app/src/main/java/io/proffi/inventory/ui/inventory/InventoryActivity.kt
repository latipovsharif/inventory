package io.proffi.inventory.ui.inventory

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
import io.proffi.inventory.network.Inventory
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.ui.scanner.ScannerActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class InventoryActivity : BaseActivity() {

    private val viewModel: InventoryViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val warehouseId = intent.getStringExtra("warehouseId") ?: ""
        val warehouseName = intent.getStringExtra("warehouseName") ?: ""

        setContent {
            MaterialTheme {
                InventoryScreen(
                    viewModel = viewModel,
                    warehouseId = warehouseId,
                    warehouseName = warehouseName,
                    onBackPressed = { finish() },
                    onInventoryStarted = { inventoryId ->
                        startActivity(
                            android.content.Intent(this, ScannerActivity::class.java).apply {
                                putExtra("inventoryId", inventoryId)
                                putExtra("warehouseName", warehouseName)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    warehouseId: String,
    warehouseName: String,
    onBackPressed: () -> Unit,
    onInventoryStarted: (String) -> Unit
) {
    val inventoriesState by viewModel.inventoriesState.collectAsState()
    val startInventoryState by viewModel.startInventoryState.collectAsState()
    val currentInventoryId by viewModel.currentInventoryId.collectAsState()

    // State для управления диалоговым окном
    var showConflictDialog by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }

    LaunchedEffect(warehouseId) {
        viewModel.loadOpenInventories(warehouseId)
    }

    LaunchedEffect(startInventoryState) {
        when (startInventoryState) {
            is StartInventoryState.Success -> {
                val inventoryId = (startInventoryState as StartInventoryState.Success).inventoryId
                onInventoryStarted(inventoryId)
                viewModel.resetStartInventoryState()
            }
            is StartInventoryState.Conflict -> {
                conflictMessage = (startInventoryState as StartInventoryState.Conflict).message
                showConflictDialog = true
            }
            else -> {}
        }
    }

    LaunchedEffect(currentInventoryId) {
        currentInventoryId?.let { inventoryId ->
            onInventoryStarted(inventoryId)
            viewModel.resetCurrentInventoryId()
        }
    }

    // Диалоговое окно для конфликта инвентаризации
    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = {
                showConflictDialog = false
                viewModel.resetStartInventoryState()
            },
            title = {
                Text(text = stringResource(R.string.inventory_conflict_dialog_title))
            },
            text = {
                Text(text = conflictMessage)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConflictDialog = false
                        viewModel.resetStartInventoryState()
                    }
                ) {
                    Text(stringResource(R.string.inventory_conflict_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(warehouseName) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.inventory_management_title),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = { viewModel.startInventory(warehouseId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = startInventoryState !is StartInventoryState.Loading,
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                if (startInventoryState is StartInventoryState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colors.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.inventory_start_new))
                }
            }

            if (startInventoryState is StartInventoryState.Error) {
                Text(
                    text = stringResource(R.string.inventory_error, (startInventoryState as StartInventoryState.Error).message),
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.inventory_open_list),
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Divider(modifier = Modifier.padding(bottom = 8.dp))

            when (val state = inventoriesState) {
                is InventoriesState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is InventoriesState.Success -> {
                    if (state.inventories.isEmpty()) {
                        Text(
                            text = stringResource(R.string.inventory_empty),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn {
                            items(state.inventories) { inventory ->
                                InventoryItem(
                                    inventory = inventory,
                                    onClick = {
                                        viewModel.selectExistingInventory(inventory.id)
                                    }
                                )
                            }
                        }
                    }
                }
                is InventoriesState.Error -> {
                    Text(
                        text = stringResource(R.string.inventory_error_loading, state.message),
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun InventoryItem(
    inventory: Inventory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
            // Иконка инвентаризации
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Информация об инвентаризации
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = inventory.warehouse.name,
                    style = MaterialTheme.typography.subtitle1
                )
                Spacer(modifier = Modifier.height(6.dp))

                // ID
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.inventory_id_label, inventory.id.take(8) + "..."),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Дата
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.inventory_started_label, inventory.startDate),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Создано пользователем
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.inventory_created_by_label, inventory.createdBy.firstName, inventory.createdBy.lastName),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Статус
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = stringResource(R.string.inventory_status_label, inventory.status),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Стрелка
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}


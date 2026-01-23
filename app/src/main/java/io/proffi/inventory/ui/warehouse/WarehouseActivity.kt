package io.proffi.inventory.ui.warehouse

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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.network.Warehouse
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.ui.inventory.InventoryActivity
import io.proffi.inventory.ui.login.LoginActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class WarehouseActivity : BaseActivity() {

    private val viewModel: WarehouseViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WarehouseScreen(
                    viewModel = viewModel,
                    onWarehouseSelected = { warehouse ->
                        startActivity(
                            android.content.Intent(this, InventoryActivity::class.java).apply {
                                putExtra("warehouseId", warehouse.id)
                                putExtra("warehouseName", warehouse.name)
                            }
                        )
                    },
                    onBackPressed = {
                        finish()
                    },
                    onLogout = {
                        startActivity(android.content.Intent(this, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun WarehouseScreen(
    viewModel: WarehouseViewModel,
    onWarehouseSelected: (Warehouse) -> Unit,
    onBackPressed: () -> Unit,
    onLogout: () -> Unit
) {
    val warehousesState by viewModel.warehousesState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warehouse_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.cd_logout))
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
                is WarehousesState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is WarehousesState.Success -> {
                    if (state.warehouses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.warehouse_empty),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            style = MaterialTheme.typography.body1
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(state.warehouses) { warehouse ->
                                WarehouseItem(
                                    warehouse = warehouse,
                                    onClick = { onWarehouseSelected(warehouse) }
                                )
                            }
                        }
                    }
                }
                is WarehousesState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.warehouse_error, state.message),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body1
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadWarehouses() }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WarehouseItem(
    warehouse: Warehouse,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
            // Иконка склада
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warehouse,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Информация о складе
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = warehouse.name,
                    style = MaterialTheme.typography.subtitle1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = warehouse.store.name,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (warehouse.address.addressString.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${warehouse.address.addressString}, ${warehouse.address.country.name}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
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


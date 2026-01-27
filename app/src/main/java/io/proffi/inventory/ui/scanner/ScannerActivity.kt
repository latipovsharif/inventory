package io.proffi.inventory.ui.scanner

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.proffi.inventory.R
import io.proffi.inventory.scanner.*
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.ScannerPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: ScannerViewModel by viewModel()
    private var inventoryId: String = ""
    private lateinit var scannerManager: ScannerManager
    private lateinit var scannerPreferences: ScannerPreferences
    private var currentScannerType: ScannerType = ScannerType.CAMERA

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBarcodeScanner()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onScanResult(barcode: String) {
        lifecycleScope.launch {
            // Автоматически отправляем на сервер с количеством 1
            viewModel.scanBarcode(inventoryId, barcode, 1.0)
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inventoryId = intent.getStringExtra("inventoryId") ?: ""
        val warehouseName = intent.getStringExtra("warehouseName") ?: ""

        scannerPreferences = ScannerPreferences(this)
        scannerManager = ScannerManager(this, this)

        // Инициализируем сканер на основе настроек
        lifecycleScope.launch {
            currentScannerType = scannerPreferences.scannerType.first()
            scannerManager.initScanner(currentScannerType)

            // Для Urovo запускаем сканер сразу
            if (currentScannerType == ScannerType.UROVO_I6310) {
                scannerManager.getCurrentScanner()?.startScan()
            }
        }

        setContent {
            MaterialTheme {
                ScannerScreen(
                    viewModel = viewModel,
                    inventoryId = inventoryId,
                    warehouseName = warehouseName,
                    currentScannerType = currentScannerType,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerManager.release()
    }

    private fun checkCameraPermissionAndScan() {
        // Для камеры проверяем разрешение, для Urovo - нет
        if (currentScannerType == ScannerType.CAMERA) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startBarcodeScanner()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } else {
            startBarcodeScanner()
        }
    }

    private fun startBarcodeScanner() {
        scannerManager.getCurrentScanner()?.startScan()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        // Только для камеры
        if (currentScannerType == ScannerType.CAMERA) {
            val barcode = CameraScanner.parseResult(requestCode, resultCode, data)
            if (barcode != null) {
                onScanResult(barcode)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    inventoryId: String,
    warehouseName: String,
    currentScannerType: ScannerType,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    val scanState by viewModel.scanState.collectAsState()
    val scannedItems by viewModel.scannedItems.collectAsState()

    LaunchedEffect(scanState) {
        if (scanState is ScanState.Success) {
            barcode = ""
            quantity = "1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title, warehouseName)) },
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
        ) {
            // Верхняя часть с формой сканирования
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_inventory_label),
                            style = MaterialTheme.typography.h6
                        )
                        Text(
                            text = stringResource(R.string.scanner_id_label, inventoryId),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.scanner_barcode_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onScanClick) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.cd_scan))
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = quantity,
                    onValueChange = {
                        // Разрешаем цифры и одну точку для дробных значений
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            quantity = it
                        }
                    },
                    label = { Text(stringResource(R.string.scanner_quantity_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val qty = quantity.toDoubleOrNull() ?: 1.0
                        viewModel.scanBarcode(inventoryId, barcode, qty)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = barcode.isNotBlank() &&
                             quantity.isNotBlank() &&
                             scanState !is ScanState.Loading,
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    if (scanState is ScanState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colors.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.scanner_send_button))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (val state = scanState) {
                    is ScanState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            elevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.scanner_success_message, state.response.message),
                                    style = MaterialTheme.typography.h6,
                                    color = MaterialTheme.colors.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.scanner_barcode_display, state.response.body.barcode),
                                    style = MaterialTheme.typography.body1
                                )
                                Text(
                                    text = stringResource(R.string.scanner_quantity_display, state.response.body.quantity),
                                    style = MaterialTheme.typography.body1
                                )
                                if (state.response.body.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.response.body.message,
                                        style = MaterialTheme.typography.body2,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    is ScanState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            elevation = 2.dp
                        ) {
                            Text(
                                text = stringResource(R.string.scanner_error_message, state.message),
                                color = MaterialTheme.colors.error,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.body1
                            )
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Заголовок списка
                if (scannedItems.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.scanner_scanned_items_title, scannedItems.size),
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Список отсканированных товаров
            if (scannedItems.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(scannedItems) { item ->
                        ScannedItemCard(
                            item = item,
                            inventoryId = inventoryId,
                            viewModel = viewModel,
                            onQuantityChange = { newQuantity ->
                                viewModel.updateItemQuantity(item.barcode, item.timestamp, newQuantity, inventoryId)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ScannedItemCard(
    item: ScannedItem,
    inventoryId: String,
    viewModel: ScannerViewModel,
    onQuantityChange: (Double) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedQuantity by remember { mutableStateOf(item.quantity.toString()) }
    var isLoadingServerQuantity by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.scanner_barcode_display, item.barcode),
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface
                    )
                    if (item.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (!isEditing) {
                            // Получаем актуальное количество с сервера перед редактированием
                            isLoadingServerQuantity = true
                            coroutineScope.launch {
                                val serverQuantity = viewModel.getCurrentQuantityFromServer(inventoryId, item.barcode)
                                isLoadingServerQuantity = false
                                if (serverQuantity != null) {
                                    editedQuantity = serverQuantity.toString()
                                } else {
                                    // Если не удалось получить с сервера, используем локальное значение
                                    editedQuantity = item.quantity.toString()
                                }
                                isEditing = true
                            }
                        } else {
                            isEditing = false
                            editedQuantity = item.quantity.toString()
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    enabled = !isLoadingServerQuantity
                ) {
                    if (isLoadingServerQuantity) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colors.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.cd_edit),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editedQuantity,
                        onValueChange = {
                            if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                editedQuantity = it
                            }
                        },
                        label = { Text(stringResource(R.string.scanner_quantity_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val newQty = editedQuantity.toDoubleOrNull()
                            if (newQty != null && newQty > 0) {
                                onQuantityChange(newQty)
                                isEditing = false
                            }
                        },
                        enabled = editedQuantity.toDoubleOrNull() != null &&
                                 editedQuantity.toDoubleOrNull()!! > 0,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(stringResource(R.string.scanner_edit_ok))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.scanner_quantity_header),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = item.quantity.toString(),
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.primary
                    )
                }
            }
        }
    }
}

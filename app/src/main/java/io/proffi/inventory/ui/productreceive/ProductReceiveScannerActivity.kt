package io.proffi.inventory.ui.productreceive

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
import androidx.compose.material.icons.filled.Check
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

class ProductReceiveScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: ProductReceiveViewModel by viewModel()
    private var moveId: String = ""
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
            runOnUiThread {
                Toast.makeText(this@ProductReceiveScannerActivity, getString(R.string.scanner_scanned_toast, barcode), Toast.LENGTH_SHORT).show()
            }
            // Автоматически отправляем на сервер с количеством 1
            viewModel.scanProductForReceive(moveId, barcode, 1.0)
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        moveId = intent.getStringExtra("moveId") ?: ""
        val fromWarehouseName = intent.getStringExtra("fromWarehouseName") ?: ""
        val toWarehouseName = intent.getStringExtra("toWarehouseName") ?: ""

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
                ProductReceiveScannerScreen(
                    viewModel = viewModel,
                    moveId = moveId,
                    fromWarehouseName = fromWarehouseName,
                    toWarehouseName = toWarehouseName,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() },
                    onConfirmReceive = {
                        viewModel.confirmReceive(moveId)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerManager.release()
    }

    private fun checkCameraPermissionAndScan() {
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
fun ProductReceiveScannerScreen(
    viewModel: ProductReceiveViewModel,
    moveId: String,
    fromWarehouseName: String,
    toWarehouseName: String,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit,
    onConfirmReceive: () -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    val scanState by viewModel.scanState.collectAsState()
    val scannedItems by viewModel.scannedItems.collectAsState()
    val confirmReceiveState by viewModel.confirmReceiveState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scanState) {
        if (scanState is ScanState.Success) {
            barcode = ""
            quantity = "1"
        }
    }

    LaunchedEffect(confirmReceiveState) {
        if (confirmReceiveState is ConfirmReceiveState.Success) {
            viewModel.resetConfirmReceiveState()
            onBackPressed()
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.product_receive_confirm_dialog_title)) },
            text = { Text(stringResource(R.string.product_receive_confirm_dialog_message, scannedItems.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onConfirmReceive()
                }) {
                    Text(stringResource(R.string.product_receive_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.product_receive_confirm_no))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_receive_scanning_title, toWarehouseName)) },
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
            // Верхняя часть с формой сканирования (30%)
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp,
                    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.05f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.product_receive_move_info),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.product_receive_from_label, fromWarehouseName),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = stringResource(R.string.product_receive_to_label, toWarehouseName),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.product_receive_id_label, moveId.take(8)),
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.product_receive_barcode_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onScanClick) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.cd_scan)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.product_receive_quantity_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val qty = quantity.toDoubleOrNull() ?: 1.0
                            viewModel.scanProductForReceive(moveId, barcode, qty)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = barcode.isNotBlank() &&
                                 quantity.isNotBlank() &&
                                 scanState !is ScanState.Loading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (scanState is ScanState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colors.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.product_receive_send_button))
                        }
                    }

                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier
                            .height(56.dp),
                        enabled = scannedItems.isNotEmpty() &&
                                 confirmReceiveState !is ConfirmReceiveState.Loading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        )
                    ) {
                        if (confirmReceiveState is ConfirmReceiveState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colors.onSecondary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.product_receive_confirm_button)
                            )
                        }
                    }
                }

                when (val state = scanState) {
                    is ScanState.Success -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                            elevation = 0.dp
                        ) {
                            Text(
                                text = stringResource(R.string.product_receive_success_message, state.response.body.message),
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colors.primary,
                                style = MaterialTheme.typography.body2
                            )
                        }
                    }
                    is ScanState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                            elevation = 0.dp
                        ) {
                            Text(
                                text = stringResource(R.string.product_receive_error_message, state.message),
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colors.error,
                                style = MaterialTheme.typography.body2
                            )
                        }
                    }
                    else -> {}
                }
            }

            Divider()

            // Нижняя часть со списком принятых товаров (70%)
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.product_receive_scanned_items_title, scannedItems.size),
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(16.dp)
                )

                if (scannedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_prompt),
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedItems) { item ->
                            ScannedReceiveItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannedReceiveItemCard(item: ScannedReceiveItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.scanner_barcode_display, item.barcode),
                        style = MaterialTheme.typography.body1
                    )
                    if (!item.productName.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.product_receive_product_name, item.productName),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.scanner_quantity_display, item.quantity.toString()),
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.message,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

package io.proffi.inventory.ui.productmove

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

class ProductMoveScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: ProductMoveViewModel by viewModel()
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
                Toast.makeText(this@ProductMoveScannerActivity, getString(R.string.scanner_scanned_toast, barcode), Toast.LENGTH_SHORT).show()
            }
            // Автоматически отправляем на сервер с количеством 1
            viewModel.scanProduct(moveId, barcode, 1.0)
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
                ProductMoveScannerScreen(
                    viewModel = viewModel,
                    moveId = moveId,
                    fromWarehouseName = fromWarehouseName,
                    toWarehouseName = toWarehouseName,
                    currentScannerType = currentScannerType,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() },
                    onCompleteMove = {
                        viewModel.completeProductMove(moveId)
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
fun ProductMoveScannerScreen(
    viewModel: ProductMoveViewModel,
    moveId: String,
    fromWarehouseName: String,
    toWarehouseName: String,
    currentScannerType: ScannerType,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit,
    onCompleteMove: () -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    val scanState by viewModel.scanState.collectAsState()
    val scannedItems by viewModel.scannedItems.collectAsState()
    val completeMoveState by viewModel.completeMoveState.collectAsState()
    var showCompleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scanState) {
        if (scanState is ScanState.Success) {
            barcode = ""
            quantity = "1"
        }
    }

    LaunchedEffect(completeMoveState) {
        if (completeMoveState is CompleteMoveState.Success) {
            viewModel.resetCompleteMoveState()
            onBackPressed()
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text(stringResource(R.string.product_move_complete_confirm_title)) },
            text = { Text(stringResource(R.string.product_move_complete_confirm_message, scannedItems.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteDialog = false
                    onCompleteMove()
                }) {
                    Text(stringResource(R.string.product_move_complete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) {
                    Text(stringResource(R.string.product_move_complete_confirm_no))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_move_scanning_title)) },
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
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.product_move_id_label, moveId),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.product_move_from_label, fromWarehouseName),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = stringResource(R.string.product_move_to_label, toWarehouseName),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.product_move_barcode_label)) },
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
                    label = { Text(stringResource(R.string.product_move_quantity_label)) },
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
                            viewModel.scanProduct(moveId, barcode, qty)
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
                            Text(stringResource(R.string.product_move_send_button))
                        }
                    }

                    Button(
                        onClick = { showCompleteDialog = true },
                        modifier = Modifier
                            .height(56.dp),
                        enabled = scannedItems.isNotEmpty() &&
                                 completeMoveState !is CompleteMoveState.Loading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        )
                    ) {
                        if (completeMoveState is CompleteMoveState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colors.onSecondary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.product_move_complete_button)
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
                                text = stringResource(R.string.product_move_success_message, state.response.body.message),
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
                                text = stringResource(R.string.product_move_error_message, state.message),
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

            // Нижняя часть со списком отсканированных товаров (70%)
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.product_move_scanned_items_title, scannedItems.size),
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
                            ScannedMoveItemCard(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannedMoveItemCard(item: ScannedMoveItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
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
                            text = stringResource(R.string.product_move_product_name, item.productName),
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

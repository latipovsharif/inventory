package io.proffi.inventory.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import org.koin.androidx.viewmodel.ext.android.viewModel

class ScannerActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModel()
    private var inventoryId: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBarcodeScanner()
        } else {
            Toast.makeText(this, "Требуется разрешение камеры", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inventoryId = intent.getStringExtra("inventoryId") ?: ""
        val warehouseName = intent.getStringExtra("warehouseName") ?: ""

        setContent {
            MaterialTheme {
                ScannerScreen(
                    viewModel = viewModel,
                    inventoryId = inventoryId,
                    warehouseName = warehouseName,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() }
                )
            }
        }
    }

    private fun checkCameraPermissionAndScan() {
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
    }

    private fun startBarcodeScanner() {
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            setPrompt("Сканируйте штрихкод")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }.initiateScan()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                // Barcode scanned successfully
                Toast.makeText(this, "Отсканирован: ${result.contents}", Toast.LENGTH_SHORT).show()
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
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    val scanState by viewModel.scanState.collectAsState()

    LaunchedEffect(scanState) {
        if (scanState is ScanState.Success) {
            barcode = ""
            quantity = "1"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сканирование - $warehouseName") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Инвентаризация",
                        style = MaterialTheme.typography.h6
                    )
                    Text(
                        text = "ID: $inventoryId",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text("Штрихкод") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = quantity,
                onValueChange = {
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        quantity = it
                    }
                },
                label = { Text("Количество") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val qty = quantity.toIntOrNull() ?: 1
                    viewModel.scanBarcode(inventoryId, barcode, qty)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = barcode.isNotBlank() &&
                         quantity.isNotBlank() &&
                         scanState !is ScanState.Loading
            ) {
                if (scanState is ScanState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colors.onPrimary
                    )
                } else {
                    Text("Отправить данные")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = scanState) {
                is ScanState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "✓ Успешно отправлено",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.primary
                            )
                            if (!state.response.productName.isNullOrBlank()) {
                                Text(
                                    text = "Товар: ${state.response.productName}",
                                    style = MaterialTheme.typography.body1
                                )
                            }
                            if (!state.response.message.isNullOrBlank()) {
                                Text(
                                    text = state.response.message,
                                    style = MaterialTheme.typography.body2
                                )
                            }
                        }
                    }
                }
                is ScanState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Ошибка: ${state.message}",
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.body1
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

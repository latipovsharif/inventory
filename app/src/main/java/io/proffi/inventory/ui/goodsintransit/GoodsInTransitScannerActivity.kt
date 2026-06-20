package io.proffi.inventory.ui.goodsintransit

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.proffi.inventory.R
import io.proffi.inventory.network.GitDetailLine
import io.proffi.inventory.scanner.*
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.ScannerPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class GoodsInTransitScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: GoodsInTransitViewModel by viewModel()
    private var documentId: String = ""
    private var externalNumber: String = ""
    private lateinit var scannerManager: ScannerManager
    private lateinit var scannerPreferences: ScannerPreferences

    private var currentScannerType by mutableStateOf(ScannerType.CAMERA)

    /** Кол-во для следующего скана товара (камера-режим управляет полем). */
    private var pendingQuantity = 1.0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startBarcodeScanner()
        else Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    override fun onScanResult(barcode: String) {
        when (viewModel.scanPhase.value) {
            is GitScanPhase.WaitingForCell -> viewModel.onCellSet(barcode)
            is GitScanPhase.CellScanned -> viewModel.onProductScanned(documentId, barcode, pendingQuantity)
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        documentId = intent.getStringExtra("documentId") ?: ""
        externalNumber = intent.getStringExtra("externalNumber") ?: ""

        scannerPreferences = ScannerPreferences(this)
        scannerManager = ScannerManager(this, this)

        lifecycleScope.launch {
            currentScannerType = scannerPreferences.scannerType.first()
            scannerManager.initScanner(currentScannerType)
            if (currentScannerType == ScannerType.UROVO_I6310) {
                scannerManager.getCurrentScanner()?.startScan()
            }
        }

        viewModel.resetPhase()
        viewModel.loadDetail(documentId)

        setContent {
            MaterialTheme {
                GoodsInTransitScannerScreen(
                    viewModel = viewModel,
                    documentId = documentId,
                    externalNumber = externalNumber,
                    currentScannerType = currentScannerType,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() },
                    onQuantityChange = { pendingQuantity = it },
                    onFinished = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentScannerType == ScannerType.UROVO_I6310) {
            scannerManager.getCurrentScanner()?.startScan()
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentScannerType == ScannerType.UROVO_I6310) {
            scannerManager.getCurrentScanner()?.stopScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerManager.release()
    }

    private fun checkCameraPermissionAndScan() {
        if (currentScannerType == ScannerType.CAMERA) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) startBarcodeScanner()
            else requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            if (barcode != null) onScanResult(barcode)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}

@Composable
fun GoodsInTransitScannerScreen(
    viewModel: GoodsInTransitViewModel,
    documentId: String,
    externalNumber: String,
    currentScannerType: ScannerType,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit,
    onQuantityChange: (Double) -> Unit,
    onFinished: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()
    val scanPhase by viewModel.scanPhase.collectAsState()
    val scanFeedback by viewModel.scanFeedback.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val scaffoldState = rememberScaffoldState()

    var showFinalizeDialog by remember { mutableStateOf(false) }

    val msgNotFound = stringResource(R.string.git_product_not_found)
    val msgFinalizeDone = stringResource(R.string.git_finalize_done)
    val msgCancelDone = stringResource(R.string.git_cancel_done)

    LaunchedEffect(scanFeedback) {
        when (val f = scanFeedback) {
            is GitScanFeedback.ProductNotFound -> {
                scaffoldState.snackbarHostState.showSnackbar(msgNotFound)
                viewModel.resetScanFeedback()
            }
            is GitScanFeedback.Success -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    "${f.productName}: +${formatQty(f.quantity)}"
                )
                viewModel.resetScanFeedback()
            }
            is GitScanFeedback.Error -> {
                scaffoldState.snackbarHostState.showSnackbar(f.message)
                viewModel.resetScanFeedback()
            }
            else -> {}
        }
    }

    LaunchedEffect(actionState) {
        when (actionState) {
            is GitActionState.FinalizeSuccess -> {
                scaffoldState.snackbarHostState.showSnackbar(msgFinalizeDone)
                viewModel.resetActionState()
                onFinished()
            }
            is GitActionState.CancelSuccess -> {
                scaffoldState.snackbarHostState.showSnackbar(msgCancelDone)
                viewModel.resetActionState()
                onFinished()
            }
            is GitActionState.Error -> {
                scaffoldState.snackbarHostState.showSnackbar((actionState as GitActionState.Error).message)
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    if (showFinalizeDialog) {
        AlertDialog(
            onDismissRequest = { showFinalizeDialog = false },
            title = { Text(stringResource(R.string.git_finalize_confirm_title)) },
            text = { Text(stringResource(R.string.git_finalize_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    showFinalizeDialog = false
                    viewModel.finalize(documentId)
                }) { Text(stringResource(R.string.git_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinalizeDialog = false }) {
                    Text(stringResource(R.string.git_confirm_no))
                }
            }
        )
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(externalNumber.ifEmpty { stringResource(R.string.git_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.cancel(documentId) }) {
                        Text(stringResource(R.string.git_cancel_doc), color = MaterialTheme.colors.error)
                    }
                }
            )
        },
        bottomBar = {
            Surface(elevation = 8.dp) {
                Button(
                    onClick = { showFinalizeDialog = true },
                    enabled = actionState !is GitActionState.Loading,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.git_finalize))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Фаза ──────────────────────────────────────────────────────────
            when (val phase = scanPhase) {
                is GitScanPhase.WaitingForCell -> CellScanBanner(
                    isCameraMode = currentScannerType == ScannerType.CAMERA,
                    onScanClick = onScanClick,
                    onCellConfirmed = { viewModel.onCellSet(it) }
                )
                is GitScanPhase.CellScanned -> ProductScanBanner(
                    boxCode = phase.boxCode,
                    isCameraMode = currentScannerType == ScannerType.CAMERA,
                    onScanClick = onScanClick,
                    onChangeCell = { viewModel.resetPhase() },
                    onProductConfirmed = { barcode, qty ->
                        onQuantityChange(qty)
                        viewModel.onProductScanned(documentId, barcode, qty)
                    }
                )
            }

            if (scanFeedback is GitScanFeedback.Loading || actionState is GitActionState.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // ── Прогресс план/факт ────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val s = detailState) {
                    is GitDetailState.Loading ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    is GitDetailState.Error ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(s.message, color = MaterialTheme.colors.error)
                        }
                    is GitDetailState.Success -> {
                        val lines = s.detail.details.orEmpty()
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(lines, key = { it.productId }) { line ->
                                GitLineCard(line)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellScanBanner(
    isCameraMode: Boolean,
    onScanClick: () -> Unit,
    onCellConfirmed: (String) -> Unit
) {
    var manual by remember { mutableStateOf("") }
    Column {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colors.primary, elevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.git_scan_cell_prompt), color = Color.White,
                    style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (isCameraMode) {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan), tint = Color.White)
                    }
                }
            }
        }
        if (isCameraMode) {
            Surface(elevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it },
                        label = { Text(stringResource(R.string.git_manual_cell_hint)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (manual.isNotBlank()) { onCellConfirmed(manual); manual = "" }
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (manual.isNotBlank()) { onCellConfirmed(manual); manual = "" } },
                        enabled = manual.isNotBlank(), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Check, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductScanBanner(
    boxCode: String,
    isCameraMode: Boolean,
    onScanClick: () -> Unit,
    onChangeCell: () -> Unit,
    onProductConfirmed: (String, Double) -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    Column {
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF388E3C), elevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.git_scan_product_prompt, boxCode), color = Color.White,
                    style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (isCameraMode) {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan), tint = Color.White)
                    }
                }
                TextButton(onClick = onChangeCell) {
                    Text(stringResource(R.string.git_change_cell), color = Color.White,
                        style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (isCameraMode) {
            Surface(elevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = barcode, onValueChange = { barcode = it },
                        label = { Text(stringResource(R.string.git_manual_barcode_hint)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = qty, onValueChange = { qty = it },
                        label = { Text(stringResource(R.string.git_qty_hint)) },
                        singleLine = true, modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val q = qty.toDoubleOrNull() ?: 1.0
                            if (barcode.isNotBlank() && q > 0) { onProductConfirmed(barcode.trim(), q); barcode = ""; qty = "1" }
                        })
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val q = qty.toDoubleOrNull() ?: 1.0
                        if (barcode.isNotBlank() && q > 0) { onProductConfirmed(barcode.trim(), q); barcode = ""; qty = "1" }
                    }, enabled = barcode.isNotBlank(), shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.git_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun GitLineCard(line: GitDetailLine) {
    val done = line.receivedQuantity >= line.orderedQuantity && line.orderedQuantity > 0
    Card(
        Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(10.dp),
        backgroundColor = if (done) Color(0xFFE8F5E9) else MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.productName ?: line.productId, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.item_article_label, line.article ?: "—"),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text(stringResource(R.string.item_barcode_label, line.barcode ?: "—"),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                if (done) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${stringResource(R.string.git_received_label)}: ${formatQty(line.receivedQuantity)}",
                    style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold,
                    color = if (done) Color(0xFF4CAF50) else MaterialTheme.colors.primary)
                Text("${stringResource(R.string.git_ordered_label)}: ${formatQty(line.orderedQuantity)}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(6.dp))
            val progress = if (line.orderedQuantity > 0)
                (line.receivedQuantity / line.orderedQuantity).toFloat().coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = progress, modifier = Modifier.fillMaxWidth(),
                color = if (done) Color(0xFF4CAF50) else MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

package io.proffi.inventory.ui.packing

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.proffi.inventory.R
import io.proffi.inventory.network.PackItem
import io.proffi.inventory.network.RecommendationDetailItem
import io.proffi.inventory.scanner.*
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.ScannerPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PackingScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: PackingViewModel by viewModel()
    private var recommendationId: String = ""
    private lateinit var scannerManager: ScannerManager
    private lateinit var scannerPreferences: ScannerPreferences

    private var currentScannerType by mutableStateOf(ScannerType.CAMERA)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startBarcodeScanner()
        else Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    override fun onScanResult(barcode: String) {
        runOnUiThread {
            Toast.makeText(this, getString(R.string.scanner_scanned_toast, barcode), Toast.LENGTH_SHORT).show()
        }
        when (viewModel.packPhase.value) {
            is PackPhase.WaitingForBox -> viewModel.onBoxCodeSet(barcode)
            is PackPhase.BoxScanned -> viewModel.onProductScanned(barcode)
        }
    }

    override fun onScanError(error: String) {
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recommendationId = intent.getStringExtra("recommendationId") ?: ""

        scannerPreferences = ScannerPreferences(this)
        scannerManager = ScannerManager(this, this)

        lifecycleScope.launch {
            currentScannerType = scannerPreferences.scannerType.first()
            scannerManager.initScanner(currentScannerType)
            if (currentScannerType == ScannerType.UROVO_I6310) {
                scannerManager.getCurrentScanner()?.startScan()
            }
        }

        viewModel.resetPackPhase()
        viewModel.loadDetail(recommendationId)

        setContent {
            MaterialTheme {
                PackingScannerScreen(
                    viewModel = viewModel,
                    currentScannerType = currentScannerType,
                    onBackPressed = { finish() },
                    onScanClick = { checkCameraPermissionAndScan() }
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
fun PackingScannerScreen(
    viewModel: PackingViewModel,
    currentScannerType: ScannerType,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()
    val packPhase by viewModel.packPhase.collectAsState()
    val packState by viewModel.packState.collectAsState()
    val scaffoldState = rememberScaffoldState()

    LaunchedEffect(packState) {
        when (packState) {
            is PackState.Error -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    message = (packState as PackState.Error).message
                )
                viewModel.resetPackState()
            }
            is PackState.Success -> {
                viewModel.resetPackState()
            }
            else -> {}
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.packing_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Phase banner ──────────────────────────────────────────────────
            when (val phase = packPhase) {
                is PackPhase.WaitingForBox -> {
                    BoxScanPhase(
                        isCameraMode = currentScannerType == ScannerType.CAMERA,
                        onScanClick = onScanClick,
                        onBoxCodeConfirmed = { viewModel.onBoxCodeSet(it) }
                    )
                }
                is PackPhase.BoxScanned -> {
                    PackPhaseBanner(
                        icon = Icons.Default.CheckCircle,
                        text = stringResource(R.string.packing_scan_product_prompt, phase.boxCode),
                        backgroundColor = Color(0xFF388E3C),
                        showScanButton = currentScannerType == ScannerType.CAMERA,
                        onScanClick = onScanClick,
                        actionLabel = stringResource(R.string.packing_change_box),
                        onAction = { viewModel.resetPackPhase() }
                    )
                }
            }

            // ── Loading indicator ─────────────────────────────────────────────
            if (packState is PackState.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // ── Content ───────────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = detailState) {
                    is PackingDetailState.Loading -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                    is PackingDetailState.Error -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(state.message, color = MaterialTheme.colors.error)
                        }
                    }
                    is PackingDetailState.Success -> {
                        val isBoxScanned = packPhase is PackPhase.BoxScanned
                        val packItems = state.detail.packItems ?: emptyList()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Items to pack
                            items(state.detail.details) { item ->
                                val packedQty = packItems
                                    .filter { it.product.id == item.product.id }
                                    .sumOf { it.quantity }
                                ScannerDetailItemCard(
                                    item = item,
                                    isActive = isBoxScanned,
                                    packedQuantity = packedQty
                                )
                            }
                            // Already packed items
                            if (packItems.isNotEmpty()) {
                                item {
                                    Text(
                                        stringResource(R.string.packing_packed_items_title, packItems.size),
                                        style = MaterialTheme.typography.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF388E3C),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                items(packItems) { packItem ->
                                    ScannerPackedItemRow(packItem)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Box scan phase: camera = text field + scan; hardware = banner only ────────
@Composable
private fun BoxScanPhase(
    isCameraMode: Boolean,
    onScanClick: () -> Unit,
    onBoxCodeConfirmed: (String) -> Unit
) {
    var manualCode by remember { mutableStateOf("") }

    Column {
        // Banner
        Surface(modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.primary, elevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Inventory2, null, tint = Color.White,
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.packing_scan_box_prompt),
                    color = Color.White,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (isCameraMode) {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan),
                            tint = Color.White)
                    }
                }
            }
        }

        // Manual entry field — shown only in camera mode
        if (isCameraMode) {
            Surface(elevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text(stringResource(R.string.packing_manual_box_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (manualCode.isNotBlank()) {
                                onBoxCodeConfirmed(manualCode)
                                manualCode = ""
                            }
                        }),
                        trailingIcon = {
                            if (manualCode.isNotBlank()) {
                                IconButton(onClick = { manualCode = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manualCode.isNotBlank()) {
                                onBoxCodeConfirmed(manualCode)
                                manualCode = ""
                            }
                        },
                        enabled = manualCode.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackPhaseBanner(
    icon: ImageVector,
    text: String,
    backgroundColor: Color,
    showScanButton: Boolean,
    onScanClick: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = backgroundColor, elevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (showScanButton) {
                IconButton(onClick = onScanClick) {
                    Icon(Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan),
                        tint = Color.White)
                }
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = Color.White,
                        style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScannerDetailItemCard(
    item: RecommendationDetailItem,
    isActive: Boolean,
    packedQuantity: Int = 0
) {
    val collected = item.collectedQuantity ?: 0
    val requested = item.requestedQuantity
    val isPackingDone = packedQuantity >= requested
    val isCollectedDone = collected >= requested
    val alpha = if (isActive || isPackingDone) 1f else 0.65f

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = if (isPackingDone) Color(0xFFE8F5E9) else MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            // ── Header row ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.product.name,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onSurface.copy(alpha = alpha)
                    )
                    Text(
                        stringResource(R.string.item_article_label, item.product.article),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = alpha * 0.6f)
                    )
                    Text(
                        stringResource(R.string.item_barcode_label, item.product.barcode),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = alpha * 0.6f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (isPackingDone) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Collected row ─────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Inventory2, null,
                    tint = if (isCollectedDone) Color(0xFF4CAF50)
                    else MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.packing_collected_label),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.item_qty_of_display, collected, requested),
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCollectedDone) Color(0xFF4CAF50)
                    else MaterialTheme.colors.onSurface.copy(alpha = alpha)
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Packed row ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.MoveToInbox, null,
                    tint = if (isPackingDone) Color(0xFF4CAF50)
                    else if (isActive) MaterialTheme.colors.primary
                    else MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.packing_packed_label),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.item_qty_of_display, packedQuantity, requested),
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isPackingDone -> Color(0xFF4CAF50)
                        isActive -> MaterialTheme.colors.primary
                        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── Progress bar (based on packed quantity) ───────────────────────
            LinearProgressIndicator(
                progress = (packedQuantity.toFloat() / requested.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (isPackingDone) Color(0xFF4CAF50) else MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun ScannerPackedItemRow(item: PackItem) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp,
        shape = RoundedCornerShape(8.dp), backgroundColor = Color(0xFFF1F8E9)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.product.name, style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(4.dp),
                color = Color(0xFF388E3C).copy(alpha = 0.15f)) {
                Text(item.boxCode, style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold, color = Color(0xFF388E3C),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("×${item.quantity}", style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
        }
    }
}

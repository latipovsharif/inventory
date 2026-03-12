package io.proffi.inventory.ui.assembly

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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.proffi.inventory.R
import io.proffi.inventory.network.RecommendationDetailItem
import io.proffi.inventory.scanner.*
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.util.ScannerPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AssemblyScannerActivity : BaseActivity(), ScannerCallback {

    private val viewModel: AssemblyViewModel by viewModel()
    private var recommendationId: String = ""
    private lateinit var scannerManager: ScannerManager
    private lateinit var scannerPreferences: ScannerPreferences

    // Реактивный тип сканера — Compose перерисуется при изменении
    private var currentScannerType by mutableStateOf(ScannerType.CAMERA)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startBarcodeScanner()
        else Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    override fun onScanResult(barcode: String) {
        runOnUiThread {
            Toast.makeText(
                this@AssemblyScannerActivity,
                getString(R.string.scanner_scanned_toast, barcode),
                Toast.LENGTH_SHORT
            ).show()
        }
        when (viewModel.scanPhase.value) {
            is ScanPhase.WaitingForBox -> viewModel.onBoxScanned(barcode)
            is ScanPhase.BoxScanned -> viewModel.onProductScanned(barcode)
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
            // Читаем настройку — обновляем State → Compose перерисуется
            currentScannerType = scannerPreferences.scannerType.first()
            scannerManager.initScanner(currentScannerType)
            if (currentScannerType == ScannerType.UROVO_I6310) {
                scannerManager.getCurrentScanner()?.startScan()
            }
        }

        // Reset phase and reload detail each time scanner is opened
        viewModel.resetScanPhase()
        viewModel.loadRecommendationDetail(recommendationId)

        setContent {
            MaterialTheme {
                AssemblyScannerScreen(
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
        // Восстанавливаем Urovo-сканер после возврата из фона
        if (currentScannerType == ScannerType.UROVO_I6310) {
            scannerManager.getCurrentScanner()?.startScan()
        }
    }

    override fun onPause() {
        super.onPause()
        // Останавливаем Urovo-сканер при уходе в фон
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
            ) {
                startBarcodeScanner()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            if (barcode != null) onScanResult(barcode)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}

@Composable
fun AssemblyScannerScreen(
    viewModel: AssemblyViewModel,
    currentScannerType: ScannerType,
    onBackPressed: () -> Unit,
    onScanClick: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()
    val scanPhase by viewModel.scanPhase.collectAsState()
    val collectState by viewModel.collectState.collectAsState()
    val scaffoldState = rememberScaffoldState()

    // Handle error messages via Snackbar
    LaunchedEffect(collectState) {
        when (collectState) {
            is CollectState.BoxNotFound -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    message = "Коробка не найдена в задании"
                )
                viewModel.resetCollectState()
            }
            is CollectState.ProductNotFound -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    message = "Товар не найден в задании"
                )
                viewModel.resetCollectState()
            }
            is CollectState.Error -> {
                scaffoldState.snackbarHostState.showSnackbar(
                    message = (collectState as CollectState.Error).message
                )
                viewModel.resetCollectState()
            }
            is CollectState.Success -> {
                viewModel.resetCollectState()
            }
            else -> {}
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assembly_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
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
            // ── Phase banner ──────────────────────────────────────────────────
            when (val phase = scanPhase) {
                is ScanPhase.WaitingForBox -> {
                    ScanPhaseBanner(
                        icon = Icons.Default.QrCodeScanner,
                        text = stringResource(R.string.assembly_scan_box_prompt),
                        backgroundColor = MaterialTheme.colors.primary,
                        showScanButton = currentScannerType == ScannerType.CAMERA,
                        onScanClick = onScanClick
                    )
                }

                is ScanPhase.BoxScanned -> {
                    ScanPhaseBanner(
                        icon = Icons.Default.CheckCircle,
                        text = stringResource(R.string.assembly_scan_product_prompt, phase.locationLabel),
                        backgroundColor = Color(0xFF388E3C),
                        showScanButton = currentScannerType == ScannerType.CAMERA,
                        onScanClick = onScanClick,
                        actionLabel = stringResource(R.string.assembly_change_box),
                        onAction = { viewModel.resetScanPhase() }
                    )
                }
            }

            // ── Collect loading indicator ─────────────────────────────────────
            if (collectState is CollectState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Items list — weight(1f) ensures correct height in Column ──────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = detailState) {
                    is DetailState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is DetailState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = state.message, color = MaterialTheme.colors.error)
                        }
                    }

                    is DetailState.Success -> {
                        val isBoxScanned = scanPhase is ScanPhase.BoxScanned
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.detail.details) { item ->
                                ScannerItemCard(
                                    item = item,
                                    isActive = isBoxScanned
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanPhaseBanner(
    icon: ImageVector,
    text: String,
    backgroundColor: Color,
    showScanButton: Boolean,
    onScanClick: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        elevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (showScanButton) {
                IconButton(onClick = onScanClick) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan),
                        tint = Color.White
                    )
                }
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
private fun ScannerItemCard(
    item: RecommendationDetailItem,
    isActive: Boolean
) {
    val collected = item.collectedQuantity ?: 0
    val isDone = collected >= item.requestedQuantity
    val alpha = if (isActive || isDone) 1f else 0.65f

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = if (!isActive) 3.dp else 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = when {
            isDone -> Color(0xFFE8F5E9)
            !isActive -> MaterialTheme.colors.surface
            else -> MaterialTheme.colors.surface
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Product header ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.onSurface.copy(alpha = alpha)
                    )
                    Text(
                        text = "Арт: ${item.product.article}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = alpha * 0.6f)
                    )
                    Text(
                        text = "Штрихкод: ${item.product.barcode}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = alpha * 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$collected",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isDone -> Color(0xFF4CAF50)
                                isActive -> MaterialTheme.colors.primary
                                else -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            }
                        )
                        Text(
                            text = "/${item.requestedQuantity}",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = "шт.",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                    )
                }

                if (isDone) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Progress bar ──────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = (collected.toFloat() / item.requestedQuantity.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (isDone) Color(0xFF4CAF50) else MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            )

            // ── Location section ──────────────────────────────────────────────
            if (item.locations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Местоположение:",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                item.locations.forEachIndexed { i, location ->
                    if (i > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Zone
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Зона:",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = location.zone.name,
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = location.zone.code,
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        // Shelf
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Text(
                                text = "Стеллаж:",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = location.shelf.name,
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = location.shelf.code,
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        // Box — the scan target
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 24.dp)
                        ) {
                            Text(
                                text = "Коробка:",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = location.box.name,
                                style = MaterialTheme.typography.body2,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colors.primary
                            ) {
                                Text(
                                    text = location.box.code,
                                    style = MaterialTheme.typography.caption,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${location.quantity} шт.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}


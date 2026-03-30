package io.proffi.inventory.ui.packing

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.network.PackItem
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationDetailItem
import io.proffi.inventory.ui.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class PackingDetailActivity : BaseActivity() {

    private val viewModel: PackingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recommendationId = intent.getStringExtra("recommendationId") ?: ""

        setContent {
            MaterialTheme {
                PackingDetailScreen(
                    viewModel = viewModel,
                    recommendationId = recommendationId,
                    onBackPressed = { finish() },
                    onStartPacking = {
                        val i = Intent(this, PackingScannerActivity::class.java)
                        i.putExtra("recommendationId", recommendationId)
                        startActivity(i)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh detail when returning from scanner
        intent.getStringExtra("recommendationId")?.let {
            viewModel.loadDetail(it)
        }
    }
}

@Composable
fun PackingDetailScreen(
    viewModel: PackingViewModel,
    recommendationId: String,
    onBackPressed: () -> Unit,
    onStartPacking: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()

    LaunchedEffect(recommendationId) {
        viewModel.loadDetail(recommendationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.packing_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = detailState) {
            is PackingDetailState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PackingDetailState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.packing_error_loading, state.message),
                            color = MaterialTheme.colors.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDetail(recommendationId) }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                }
            }
            is PackingDetailState.Success -> {
                PackingDetailContent(
                    detail = state.detail,
                    modifier = Modifier.padding(padding),
                    onStartPacking = onStartPacking
                )
            }
        }
    }
}

@Composable
private fun PackingDetailContent(
    detail: RecommendationDetail,
    modifier: Modifier = Modifier,
    onStartPacking: () -> Unit
) {
    val packItems = detail.packItems ?: emptyList()
    val statusColor = when (detail.status.lowercase()) {
        "collecting" -> Color(0xFF1976D2)
        "packaging" -> Color(0xFFF57C00)
        else -> MaterialTheme.colors.primary
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            Card(elevation = 4.dp, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MoveToInbox, null,
                            tint = statusColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${detail.fromWarehouse.name} → ${detail.toWarehouse.name}",
                            style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.15f)) {
                        Text(detail.status,
                            style = MaterialTheme.typography.caption,
                            fontWeight = FontWeight.Bold, color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (!detail.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(detail.notes, style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // ── Start button ──────────────────────────────────────────────────────
        item {
            Button(
                onClick = onStartPacking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.packing_start_button))
            }
        }

        // ── Items to pack section ─────────────────────────────────────────────
        item {
            Text(stringResource(R.string.packing_items_title, detail.details.size),
                style = MaterialTheme.typography.subtitle2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp))
        }
        items(detail.details) { item ->
            PackingDetailItemCard(item)
        }

        // ── Packed items section ──────────────────────────────────────────────
        if (packItems.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.packing_packed_items_title, packItems.size),
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF388E3C))
            }
            items(packItems) { packItem ->
                PackedItemCard(packItem)
            }
        }
    }
}

@Composable
private fun PackingDetailItemCard(item: RecommendationDetailItem) {
    val collected = item.collectedQuantity ?: 0
    val isDone = collected >= item.requestedQuantity

    Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = if (isDone) Color(0xFFE8F5E9) else MaterialTheme.colors.surface) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.SemiBold)
                Text("Арт: ${item.product.article}", style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                Text("Штрихкод: ${item.product.barcode}", style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$collected", style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) Color(0xFF4CAF50) else MaterialTheme.colors.primary)
                    Text("/${item.requestedQuantity}", style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                }
                Text("шт.", style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
            }
            if (isDone) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.CheckCircle, null,
                    tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PackedItemCard(item: PackItem) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color(0xFFF1F8E9)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Inventory2, null, tint = Color(0xFF388E3C),
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Medium)
                Text("Штрихкод: ${item.itemBarcode}", style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(8.dp),
                color = Color(0xFF388E3C).copy(alpha = 0.15f)) {
                Text(
                    text = stringResource(R.string.packing_box_label, item.boxCode),
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF388E3C),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("×${item.quantity}", style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
        }
    }
}

package io.proffi.inventory.ui.assembly

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
import io.proffi.inventory.network.RecommendationDetail
import io.proffi.inventory.network.RecommendationDetailItem
import io.proffi.inventory.ui.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class AssemblyDetailActivity : BaseActivity() {

    private val viewModel: AssemblyViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recommendationId = intent.getStringExtra("recommendationId") ?: ""

        setContent {
            MaterialTheme {
                AssemblyDetailScreen(
                    viewModel = viewModel,
                    recommendationId = recommendationId,
                    onBackPressed = { finish() },
                    onStartAssembly = {
                        val intent = Intent(this, AssemblyScannerActivity::class.java)
                        intent.putExtra("recommendationId", recommendationId)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun AssemblyDetailScreen(
    viewModel: AssemblyViewModel,
    recommendationId: String,
    onBackPressed: () -> Unit,
    onStartAssembly: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()

    LaunchedEffect(recommendationId) {
        viewModel.loadRecommendationDetail(recommendationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assembly_detail_title)) },
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
        when (val state = detailState) {
            is DetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is DetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.assembly_error_loading, state.message),
                            color = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadRecommendationDetail(recommendationId) }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                }
            }

            is DetailState.Success -> {
                AssemblyDetailContent(
                    detail = state.detail,
                    modifier = Modifier.padding(padding),
                    onStartAssembly = onStartAssembly
                )
            }
        }
    }
}

@Composable
fun AssemblyDetailContent(
    detail: RecommendationDetail,
    modifier: Modifier = Modifier,
    onStartAssembly: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {

        // Header card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            elevation = 4.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${detail.fromWarehouse.name} → ${detail.toWarehouse.name}",
                        style = MaterialTheme.typography.h6
                    )
                }

                if (!detail.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail.notes,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = detail.status,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${detail.createdBy.firstName} ${detail.createdBy.lastName}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Start button
        Button(
            onClick = onStartAssembly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.assembly_start_button),
                style = MaterialTheme.typography.button
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.assembly_positions_title, detail.details.size),
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(detail.details) { item ->
                DetailItemCard(item = item)
            }
        }
    }
}

@Composable
fun DetailItemCard(item: RecommendationDetailItem) {
    val collected = item.collectedQuantity ?: 0
    val isDone = collected >= item.requestedQuantity

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = if (isDone) Color(0xFFE8F5E9) else MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Арт: ${item.product.article}  •  ${item.product.barcode}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$collected",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) Color(0xFF4CAF50) else MaterialTheme.colors.primary
                    )
                    Text(
                        text = "/${item.requestedQuantity}",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }

                if (isDone) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Progress bar
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = (collected.toFloat() / item.requestedQuantity.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = if (isDone) Color(0xFF4CAF50) else MaterialTheme.colors.primary,
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            )

            // Locations
            if (item.locations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
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

                item.locations.forEachIndexed { index, location ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Zone
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Зона:  ",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
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
                                color = MaterialTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        // Shelf
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Text(
                                text = "Стеллаж:  ",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
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
                                color = MaterialTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        // Box (most important — scan target)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 24.dp)
                        ) {
                            Text(
                                text = "Коробка:  ",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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


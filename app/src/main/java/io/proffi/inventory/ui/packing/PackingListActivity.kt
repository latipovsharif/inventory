package io.proffi.inventory.ui.packing

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.proffi.inventory.network.Recommendation
import io.proffi.inventory.ui.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class PackingListActivity : BaseActivity() {

    private val viewModel: PackingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PackingListScreen(
                    viewModel = viewModel,
                    onBackPressed = { finish() },
                    onRecommendationClick = { recommendationId ->
                        val intent = Intent(this, PackingDetailActivity::class.java)
                        intent.putExtra("recommendationId", recommendationId)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun PackingListScreen(
    viewModel: PackingViewModel,
    onBackPressed: () -> Unit,
    onRecommendationClick: (String) -> Unit
) {
    val listState by viewModel.listState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecommendations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.packing_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = listState) {
            is PackingListState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PackingListState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.packing_error_loading, state.message),
                            color = MaterialTheme.colors.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadRecommendations() }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                }
            }
            is PackingListState.Success,
            is PackingListState.LoadingMore -> {
                val items = when (state) {
                    is PackingListState.Success -> state.items
                    is PackingListState.LoadingMore -> state.items
                    else -> emptyList()
                }
                val hasMore = (state as? PackingListState.Success)?.hasMore ?: false
                val isLoadingMore = state is PackingListState.LoadingMore

                if (items.isEmpty() && !isLoadingMore) {
                    Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                        Text(stringResource(R.string.packing_empty),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                } else {
                    val lazyState = rememberLazyListState()
                    val lastVisible by remember {
                        derivedStateOf {
                            lazyState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        }
                    }
                    LaunchedEffect(lastVisible) {
                        if (hasMore && items.isNotEmpty() && lastVisible >= items.size - 3) {
                            viewModel.loadNextPage()
                        }
                    }
                    LazyColumn(
                        state = lazyState,
                        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { rec ->
                            PackingRecommendationCard(
                                recommendation = rec,
                                onClick = { onRecommendationClick(rec.id) }
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackingRecommendationCard(
    recommendation: Recommendation,
    onClick: () -> Unit
) {
    val statusColor = when (recommendation.status.lowercase()) {
        "collecting" -> Color(0xFF1976D2)
        "packaging" -> Color(0xFFF57C00)
        "packed", "completed" -> Color(0xFF388E3C)
        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MoveToInbox,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${recommendation.fromWarehouse.name} → ${recommendation.toWarehouse.name}",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "ID: ${recommendation.id.take(8)}…",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = recommendation.status,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Icon(Icons.Default.Person, contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${recommendation.createdBy.firstName} ${recommendation.createdBy.lastName}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.packing_positions_count,
                        recommendation.totalProducts, recommendation.totalRequested),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

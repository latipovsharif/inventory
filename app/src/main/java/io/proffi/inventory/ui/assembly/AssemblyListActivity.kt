package io.proffi.inventory.ui.assembly

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.network.Recommendation
import io.proffi.inventory.ui.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class AssemblyListActivity : BaseActivity() {

    private val viewModel: AssemblyViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AssemblyListScreen(
                    viewModel = viewModel,
                    onBackPressed = { finish() },
                    onRecommendationClick = { recommendationId ->
                        val intent = Intent(this, AssemblyDetailActivity::class.java)
                        intent.putExtra("recommendationId", recommendationId)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun AssemblyListScreen(
    viewModel: AssemblyViewModel,
    onBackPressed: () -> Unit,
    onRecommendationClick: (String) -> Unit
) {
    val recommendationsState by viewModel.recommendationsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRecommendations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assembly_list_title)) },
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
        when (val state = recommendationsState) {
            is RecommendationsState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is RecommendationsState.Error -> {
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
                        Button(onClick = { viewModel.loadRecommendations() }) {
                            Text(stringResource(R.string.warehouse_retry))
                        }
                    }
                }
            }

            is RecommendationsState.Success,
            is RecommendationsState.LoadingMore -> {
                val items = when (state) {
                    is RecommendationsState.Success -> state.items
                    is RecommendationsState.LoadingMore -> state.items
                    else -> emptyList()
                }
                val hasMore = (state as? RecommendationsState.Success)?.hasMore ?: false
                val isLoadingMore = state is RecommendationsState.LoadingMore

                if (items.isEmpty() && !isLoadingMore) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.assembly_empty),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val lastVisibleIndex by remember {
                        derivedStateOf {
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        }
                    }

                    LaunchedEffect(lastVisibleIndex) {
                        if (hasMore && items.isNotEmpty() && lastVisibleIndex >= items.size - 3) {
                            viewModel.loadNextPage()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { recommendation ->
                            RecommendationCard(
                                recommendation = recommendation,
                                onClick = { onRecommendationClick(recommendation.id) }
                            )
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
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
fun RecommendationCard(
    recommendation: Recommendation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Route
                Text(
                    text = "${recommendation.fromWarehouse.name} → ${recommendation.toWarehouse.name}",
                    style = MaterialTheme.typography.subtitle1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Products / requested
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.assembly_products_count,
                            recommendation.totalProducts,
                            recommendation.totalRequested
                        ),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Author
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${recommendation.createdBy.firstName} ${recommendation.createdBy.lastName}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recommendation.createdAt,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Status chip
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = recommendation.status,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}


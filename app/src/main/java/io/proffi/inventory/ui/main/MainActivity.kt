package io.proffi.inventory.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.ui.base.BaseActivity
import io.proffi.inventory.ui.login.LoginActivity
import io.proffi.inventory.ui.warehouse.WarehouseActivity
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var contentKey by remember { mutableStateOf(0) }
            val activity = this@MainActivity

            AnimatedContent(
                targetState = contentKey,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "language_change_animation"
            ) { _ ->
                MaterialTheme {
                MainScreen(
                    onLogout = {
                        // Очистить токены и вернуться на экран входа
                        startActivity(Intent(activity, LoginActivity::class.java))
                        finish()
                    },
                    onInventoryClick = {
                        // Открыть экран выбора склада для инвентаризации
                        startActivity(Intent(activity, WarehouseActivity::class.java))
                    },
                    onProductMoveClick = {
                        // Открыть экран перемещения товара
                        startActivity(Intent(activity, io.proffi.inventory.ui.productmove.ProductMoveSelectionActivity::class.java))
                    },
                    onProductReceiveClick = {
                        // Открыть экран приёмки товара
                        startActivity(Intent(activity, io.proffi.inventory.ui.productreceive.ProductReceiveSelectionActivity::class.java))
                    },
                    onSettingsClick = {
                        // Открыть экран настроек
                        startActivity(Intent(activity, io.proffi.inventory.ui.settings.SettingsActivity::class.java))
                    }
                )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onInventoryClick: () -> Unit,
    onProductMoveClick: () -> Unit,
    onProductReceiveClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            scaffoldState.drawerState.open()
                        }
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu_open))
                    }
                }
            )
        },
        drawerContent = {
            DrawerContent(
                onInventoryClick = {
                    scope.launch {
                        scaffoldState.drawerState.close()
                    }
                    onInventoryClick()
                },
                onProductMoveClick = {
                    scope.launch {
                        scaffoldState.drawerState.close()
                    }
                    onProductMoveClick()
                },
                onProductReceiveClick = {
                    scope.launch {
                        scaffoldState.drawerState.close()
                    }
                    onProductReceiveClick()
                },
                onSettingsClick = {
                    scope.launch {
                        scaffoldState.drawerState.close()
                    }
                    onSettingsClick()
                },
                onLogout = {
                    scope.launch {
                        scaffoldState.drawerState.close()
                    }
                    onLogout()
                }
            )
        }
    ) { padding ->
        // Основное содержимое экрана
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.h4
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Выберите раздел в меню",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Быстрые действия
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onInventoryClick),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colors.primary
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.menu_inventory),
                            style = MaterialTheme.typography.h6
                        )
                        Text(
                            text = "Начать инвентаризацию",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    onInventoryClick: () -> Unit,
    onProductMoveClick: () -> Unit,
    onProductReceiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        // Заголовок меню
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.h6
                )
                Text(
                    text = "Система инвентаризации",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Divider()

        Spacer(modifier = Modifier.height(8.dp))

        // Пункт меню: Инвентаризация
        DrawerMenuItem(
            icon = Icons.Default.Inventory,
            text = stringResource(R.string.menu_inventory),
            onClick = onInventoryClick
        )

        // Пункт меню: Перемещение товара
        DrawerMenuItem(
            icon = Icons.Default.SwapHoriz,
            text = stringResource(R.string.menu_product_move),
            onClick = onProductMoveClick
        )

        // Пункт меню: Приёмка товара
        DrawerMenuItem(
            icon = Icons.Default.Inbox,
            text = stringResource(R.string.menu_product_receive),
            onClick = onProductReceiveClick
        )

        // Пункт меню: Склады
        DrawerMenuItem(
            icon = Icons.Default.Store,
            text = stringResource(R.string.menu_warehouses),
            onClick = onInventoryClick // Пока ведёт туда же
        )

        Spacer(modifier = Modifier.weight(1f))

        Divider()

        // Пункт меню: Настройки
        DrawerMenuItem(
            icon = Icons.Default.Settings,
            text = stringResource(R.string.menu_settings),
            onClick = onSettingsClick
        )

        Divider()

        // Пункт меню: Выход
        DrawerMenuItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            text = stringResource(R.string.menu_logout),
            onClick = onLogout
        )
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colors.onSurface
        )

        Spacer(modifier = Modifier.width(32.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.body1
        )
    }
}

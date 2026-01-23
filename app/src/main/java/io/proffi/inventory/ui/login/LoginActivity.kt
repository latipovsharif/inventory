package io.proffi.inventory.ui.login

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.proffi.inventory.R
import io.proffi.inventory.ui.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : BaseActivity() {

    private val viewModel: LoginViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        // Navigate to main menu
                        startActivity(
                            android.content.Intent(
                                this,
                                io.proffi.inventory.ui.main.MainActivity::class.java
                            )
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val loginState by viewModel.loginState.collectAsState()
    var hasNavigated by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success && !hasNavigated) {
            hasNavigated = true
            onLoginSuccess()
            viewModel.resetLoginState()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Иконка приложения
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colors.primary
                )

                // Заголовок
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.h4,
                    color = MaterialTheme.colors.primary
                )

                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.subtitle1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Карточка с формой
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Email поле
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.login_email)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Password поле
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.login_password)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible)
                                            Icons.Default.Visibility
                                        else
                                            Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible)
                                            "Скрыть пароль"
                                        else
                                            "Показать пароль"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            visualTransformation = if (passwordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (email.isNotBlank() && password.isNotBlank()) {
                                        viewModel.login(email, password)
                                    }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Кнопка входа
                        Button(
                            onClick = { viewModel.login(email, password) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = loginState !is LoginState.Loading &&
                                     email.isNotBlank() && password.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.elevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            if (loginState is LoginState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colors.onPrimary
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.login_button),
                                    style = MaterialTheme.typography.button
                                )
                            }
                        }

                        // Сообщение об ошибке
                        if (loginState is LoginState.Error) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = (loginState as LoginState.Error).message,
                                    color = MaterialTheme.colors.error,
                                    style = MaterialTheme.typography.body2,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Дополнительная информация внизу
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "© 2026 proffi.io",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

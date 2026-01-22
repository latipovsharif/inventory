package io.proffi.inventory.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.proffi.inventory.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Empty)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            val result = authRepository.login(email, password)

            _loginState.value = if (result.isSuccess) {
                val response = result.getOrNull()!!
                LoginState.Success(response.access_token, response.refresh_token, response.user)
            } else {
                LoginState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}

sealed class LoginState {
    object Empty : LoginState()
    object Loading : LoginState()
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val user: io.proffi.inventory.network.User
    ) : LoginState()
    data class Error(val message: String) : LoginState()
}

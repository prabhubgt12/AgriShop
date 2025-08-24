package com.fertipos.agroshop.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false
    )

    private val _loginState = MutableStateFlow(UiState())
    val loginState = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow(UiState())
    val registerState = _registerState.asStateFlow()

    fun login(username: String, password: String) {
        _loginState.value = UiState(loading = true)
        viewModelScope.launch {
            val result = repo.login(username, password)
            _loginState.value = result.fold(
                onSuccess = { UiState(success = true) },
                onFailure = { UiState(error = it.message ?: "Login failed") }
            )
        }
    }

    fun register(username: String, password: String, confirm: String) {
        if (username.isBlank() || password.isBlank()) {
            _registerState.value = UiState(error = "Username and password required")
            return
        }
        if (password != confirm) {
            _registerState.value = UiState(error = "Passwords do not match")
            return
        }
        _registerState.value = UiState(loading = true)
        viewModelScope.launch {
            val result = repo.register(username, password)
            _registerState.value = result.fold(
                onSuccess = { UiState(success = true) },
                onFailure = { UiState(error = it.message ?: "Registration failed") }
            )
        }
    }

    fun clearErrors() {
        _loginState.value = _loginState.value.copy(error = null)
        _registerState.value = _registerState.value.copy(error = null)
    }
}

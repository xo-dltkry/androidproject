package viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.AuthRepository
import data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    var currentUser by mutableStateOf<User?>(null)
        private set

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                Log.d("AuthViewModel", "Initializing auth state")
                val user = authRepository.currentUser.first()
                Log.d("AuthViewModel", "Initial user state: ${user?.email}")
                currentUser = user
                _isLoggedIn.value = user != null
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to initialize auth state", e)
                error = e.message ?: "Failed to initialize auth state"
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                Log.d("AuthViewModel", "Attempting to login: $email")
                authRepository.login(email, password)
                    .onSuccess { user ->
                        currentUser = user
                        _isLoggedIn.value = true
                        Log.d("AuthViewModel", "Login successful")
                    }
                    .onFailure { e ->
                        error = e.message ?: "Login failed"
                        Log.e("AuthViewModel", "Login failed", e)
                    }
            } catch (e: Exception) {
                error = e.message ?: "Login failed"
                Log.e("AuthViewModel", "Login failed", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun register(email: String, username: String, password: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                Log.d("AuthViewModel", "Attempting to register: $email")
                authRepository.register(email, username, password)
                    .onSuccess { user ->
                        currentUser = user
                        _isLoggedIn.value = true
                        Log.d("AuthViewModel", "Registration successful")
                    }
                    .onFailure { e ->
                        error = e.message ?: "Registration failed"
                        Log.e("AuthViewModel", "Registration failed", e)
                    }
            } catch (e: Exception) {
                error = e.message ?: "Registration failed"
                Log.e("AuthViewModel", "Registration failed", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                Log.d("AuthViewModel", "Attempting to logout")
                _isLoggedIn.value = false
                currentUser = null
                authRepository.logout()
                Log.d("AuthViewModel", "Logout successful")
            } catch (e: Exception) {
                error = e.message ?: "Logout failed"
                Log.e("AuthViewModel", "Logout failed", e)
            } finally {
                isLoading = false
            }
        }
    }
} 
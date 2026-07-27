package com.example.snapget.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.model.auth.AuthState
import com.example.snapget.feature.auth.data.AuthRepository
import com.example.snapget.feature.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing authentication state and operations
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        checkAuthStatus()
    }

    /**
     * Check current authentication status
     */
    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val user = authRepository.getCurrentUser()
                _authState.value = if (user != null) {
                    // Dang ky lai FCM token moi lan mo app (fix 2026-07-27): lan dang ky
                    // luc login co the fail ma phien van giu -> khong nhan push mai mai
                    launch { authRepository.ensureFcmTokenRegistered() }
                    AuthState.Authenticated(user)
                } else {
                    AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    /**
     * Initiate Google Sign-In (Credential Manager + Firebase Auth)
     */
    fun loginWithGoogle(activity: androidx.activity.ComponentActivity) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _authState.value = AuthState.Loading
                val user = authRepository.signInWithGoogle(activity)
                _authState.value = if (user != null) {
                    AuthState.Authenticated(user)
                } else {
                    AuthState.Error("Google sign-in failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Google sign-in failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Logout current user
     */
    fun logout() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = authRepository.logout()
                if (success) {
                    _authState.value = AuthState.Unauthenticated
                    // Widget chuyen sang trang thai "Sign in" ngay (xoa snapshot + anh)
                    widgetRefresher.markSignedOut()
                } else {
                    _authState.value = AuthState.Error("Failed to logout")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Logout failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Logout from all devices
     */
    fun logoutFromAllDevices() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = authRepository.logoutFromAllDevices()
                if (success) {
                    _authState.value = AuthState.Unauthenticated
                } else {
                    _authState.value = AuthState.Error("Failed to logout from all devices")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Logout failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh authentication state
     */
    fun refreshAuth() {
        checkAuthStatus()
    }

    /**
     * Clear error state
     */
    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Register a new user with email and password
     */
    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _authState.value = AuthState.Loading
                val user = authRepository.register(email, password, name)
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Error("Registration failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Registration failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Login with email and password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _authState.value = AuthState.Loading
                val user = authRepository.login(email, password)
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Error("Login failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Login failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Request password reset
     */
    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = authRepository.resetPassword(email)
                if (success) {
                    _authState.value = AuthState.PasswordResetSent
                } else {
                    _authState.value = AuthState.Error("Failed to send password reset")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Password reset failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}

package com.example.myiapp.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.CommunityData
import com.example.myiapp.data.model.UserData
import com.example.myiapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AuthViewModel — handles all authentication flows
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • Login (with role selector: Resident / Staff / Admin)
 *  • Register (name, email, phone, password, role, community)
 *  • OTP verification popup
 *  • Forgot password → OTP → new password
 *  • Logout
 *  • Loads active communities for the register dropdown
 *
 *  All state is exposed via StateFlow for Compose.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val sessionManager = SessionManager(application)
    private val repository = AuthRepository(sessionManager)

    // ═════════════════════════════════════════════
    // LOGIN STATE
    // ═════════════════════════════════════════════
    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    // ═════════════════════════════════════════════
    // REGISTER STATE
    // ═════════════════════════════════════════════
    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    // ═════════════════════════════════════════════
    // OTP STATE (shared between register + forgot password)
    // ═════════════════════════════════════════════
    private val _otpState = MutableStateFlow(OtpUiState())
    val otpState: StateFlow<OtpUiState> = _otpState.asStateFlow()

    // ═════════════════════════════════════════════
    // FORGOT PASSWORD STATE
    // ═════════════════════════════════════════════
    private val _forgotState = MutableStateFlow(ForgotPasswordUiState())
    val forgotState: StateFlow<ForgotPasswordUiState> = _forgotState.asStateFlow()

    // ═════════════════════════════════════════════
    // LOGOUT STATE
    // ═════════════════════════════════════════════
    private val _logoutState = MutableStateFlow(LogoutUiState())
    val logoutState: StateFlow<LogoutUiState> = _logoutState.asStateFlow()

    // ═════════════════════════════════════════════
    // SESSION (for splash screen)
    // ═════════════════════════════════════════════
    private val _sessionState = MutableStateFlow(SessionUiState())
    val sessionState: StateFlow<SessionUiState> = _sessionState.asStateFlow()

    // ═════════════════════════════════════════════
    // ONE-SHOT MESSAGES (for Snackbar)
    // ═════════════════════════════════════════════
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    // ═════════════════════════════════════════════
    // LOGIN
    // ═════════════════════════════════════════════

    fun updateLoginEmail(email: String) {
        _loginState.update { it.copy(email = email, error = null) }
    }

    fun updateLoginPassword(password: String) {
        _loginState.update { it.copy(password = password, error = null) }
    }

    fun updateLoginRole(role: String) {
        _loginState.update { it.copy(role = role, error = null) }
    }

    fun login() {
        val state = _loginState.value

        // Validation
        if (state.email.isBlank() || state.password.isBlank()) {
            _loginState.update { it.copy(error = "Email and password are required") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _loginState.update { it.copy(error = "Please enter a valid email") }
            return
        }
        if (state.password.length < 6) {
            _loginState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }

            repository.login(
                email = state.email.trim(),
                password = state.password,
                role = state.role
            ).fold(
                onSuccess = { response ->
                    if (response.success && response.user != null) {
                        val user = response.user
                        _loginState.update {
                            it.copy(
                                isLoading = false,
                                loginSucceeded = true,
                                loggedInUser = user,
                                error = null
                            )
                        }
                        Log.d(TAG, "✅ Login success: ${user.email} (${user.role})")
                    } else {
                        _loginState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message ?: "Login failed"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _loginState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Network error"
                        )
                    }
                }
            )
        }
    }

    fun consumeLoginSuccess() {
        _loginState.update { it.copy(loginSucceeded = false) }
    }

    // ═════════════════════════════════════════════
    // REGISTER
    // ═════════════════════════════════════════════

    fun updateRegisterField(
        name: String? = null,
        email: String? = null,
        phone: String? = null,
        password: String? = null,
        role: String? = null,
        communityId: String? = null,
        communityName: String? = null
    ) {
        _registerState.update { current ->
            current.copy(
                name = name ?: current.name,
                email = email ?: current.email,
                phone = phone ?: current.phone,
                password = password ?: current.password,
                role = role ?: current.role,
                communityId = communityId ?: current.communityId,
                communityName = communityName ?: current.communityName,
                error = null
            )
        }
    }

    /**
     * Load active communities for the register dropdown.
     * Called when the register screen opens.
     */
    fun loadCommunities() {
        viewModelScope.launch {
            _registerState.update { it.copy(isLoadingCommunities = true) }

            repository.getActiveCommunities().fold(
                onSuccess = { response ->
                    _registerState.update {
                        it.copy(
                            isLoadingCommunities = false,
                            communities = response.communities
                        )
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to load communities", e)
                    _registerState.update {
                        it.copy(
                            isLoadingCommunities = false,
                            error = "Could not load communities"
                        )
                    }
                }
            )
        }
    }

    fun register() {
        val state = _registerState.value

        // Validation
        when {
            state.name.isBlank() ->
                _registerState.update { it.copy(error = "Name is required") }

            state.email.isBlank() ->
                _registerState.update { it.copy(error = "Email is required") }

            !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches() ->
                _registerState.update { it.copy(error = "Invalid email address") }

            state.phone.isBlank() || state.phone.length < 7 ->
                _registerState.update { it.copy(error = "Please enter a valid phone number") }

            state.password.length < 6 ->
                _registerState.update { it.copy(error = "Password must be at least 6 characters") }

            state.communityId.isBlank() ->
                _registerState.update { it.copy(error = "Please select your community") }

            else -> performRegister(state)
        }
    }

    private fun performRegister(state: RegisterUiState) {
        viewModelScope.launch {
            _registerState.update { it.copy(isLoading = true, error = null) }

            repository.register(
                name = state.name.trim(),
                email = state.email.trim().lowercase(),
                phone = state.phone.trim(),
                password = state.password,
                role = state.role,
                communityId = state.communityId
            ).fold(
                onSuccess = { response ->
                    if (response.success) {
                        // Open OTP popup for email verification
                        _otpState.value = OtpUiState(
                            isVisible = true,
                            email = state.email.trim().lowercase(),
                            purpose = OtpPurpose.REGISTER,
                            expiresInSeconds = 600
                        )
                        _registerState.update {
                            it.copy(isLoading = false, error = null)
                        }
                        Log.d(TAG, "📧 OTP sent to ${state.email}")
                    } else {
                        _registerState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message ?: "Registration failed"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _registerState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Network error"
                        )
                    }
                }
            )
        }
    }

    // ═════════════════════════════════════════════
    // OTP VERIFICATION
    // ═════════════════════════════════════════════

    fun updateOtp(otp: String) {
        _otpState.update { it.copy(otp = otp, error = null) }
    }

    fun dismissOtpPopup() {
        _otpState.update { it.copy(isVisible = false) }
    }

    fun verifyOtp() {
        val state = _otpState.value

        if (state.otp.isBlank() || state.otp.length != 6) {
            _otpState.update { it.copy(error = "Please enter the 6-digit code") }
            return
        }

        viewModelScope.launch {
            _otpState.update { it.copy(isLoading = true, error = null) }

            repository.verifyOtp(state.email, state.otp.trim()).fold(
                onSuccess = { response ->
                    if (response.success) {
                        _otpState.update {
                            it.copy(
                                isLoading = false,
                                isVisible = false,
                                isVerified = true,
                                error = null
                            )
                        }
                        _message.value = when (state.purpose) {
                            OtpPurpose.REGISTER -> "Email verified! You can now login."
                            OtpPurpose.FORGOT_PASSWORD -> "OTP verified. Set a new password."
                        }

                        if (state.purpose == OtpPurpose.REGISTER) {
                            // Clear register form for return to login
                            _registerState.value = RegisterUiState()
                        }
                    } else {
                        _otpState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message ?: "Invalid OTP"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _otpState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Network error"
                        )
                    }
                }
            )
        }
    }

    fun resendOtp() {
        val state = _otpState.value
        if (state.email.isBlank()) return

        viewModelScope.launch {
            _otpState.update { it.copy(isLoading = true, error = null) }

            repository.forgotPassword(state.email).fold(
                onSuccess = { response ->
                    _otpState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            expiresInSeconds = 600
                        )
                    }
                    _message.value = response.message ?: "New OTP sent"
                },
                onFailure = { e ->
                    _otpState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to resend")
                    }
                }
            )
        }
    }

    fun consumeOtpVerified() {
        _otpState.update { it.copy(isVerified = false) }
    }

    // ═════════════════════════════════════════════
    // FORGOT PASSWORD
    // ═════════════════════════════════════════════

    fun updateForgotEmail(email: String) {
        _forgotState.update { it.copy(email = email, error = null) }
    }

    fun updateForgotNewPassword(password: String) {
        _forgotState.update { it.copy(newPassword = password, error = null) }
    }

    fun sendForgotPasswordOtp() {
        val state = _forgotState.value

        if (state.email.isBlank()) {
            _forgotState.update { it.copy(error = "Email is required") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _forgotState.update { it.copy(error = "Please enter a valid email") }
            return
        }

        viewModelScope.launch {
            _forgotState.update { it.copy(isLoading = true, error = null) }

            repository.forgotPassword(state.email.trim().lowercase()).fold(
                onSuccess = { response ->
                    if (response.success) {
                        _forgotState.update {
                            it.copy(
                                isLoading = false,
                                step = ForgotStep.OTP,
                                error = null
                            )
                        }
                        _otpState.value = OtpUiState(
                            isVisible = true,
                            email = state.email.trim().lowercase(),
                            purpose = OtpPurpose.FORGOT_PASSWORD,
                            expiresInSeconds = 600
                        )
                    } else {
                        _forgotState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message ?: "Failed to send OTP"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _forgotState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Network error"
                        )
                    }
                }
            )
        }
    }

    /**
     * Called after OTP verification when the user enters a new password.
     */
    fun resetPassword() {
        val forgot = _forgotState.value
        val otpState = _otpState.value

        if (forgot.newPassword.length < 6) {
            _forgotState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }
        if (forgot.newPassword != forgot.confirmPassword) {
            _forgotState.update { it.copy(error = "Passwords do not match") }
            return
        }
        if (otpState.otp.isBlank()) {
            _forgotState.update { it.copy(error = "Please enter the OTP first") }
            return
        }

        viewModelScope.launch {
            _forgotState.update { it.copy(isLoading = true, error = null) }

            repository.resetPassword(
                email = forgot.email.trim().lowercase(),
                otp = otpState.otp.trim(),
                newPassword = forgot.newPassword
            ).fold(
                onSuccess = { response ->
                    if (response.success) {
                        _forgotState.value = ForgotPasswordUiState()
                        _otpState.value = OtpUiState()
                        _message.value = "Password reset successfully. Please login."
                    } else {
                        _forgotState.update {
                            it.copy(
                                isLoading = false,
                                error = response.message ?: "Password reset failed"
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _forgotState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Network error"
                        )
                    }
                }
            )
        }
    }

    fun updateForgotConfirmPassword(password: String) {
        _forgotState.update { it.copy(confirmPassword = password, error = null) }
    }

    // ═════════════════════════════════════════════
    // LOGOUT
    // ═════════════════════════════════════════════

    fun logout() {
        viewModelScope.launch {
            _logoutState.update { it.copy(isLoading = true) }
            repository.logout()
            _logoutState.update { it.copy(isLoading = false, loggedOut = true) }
        }
    }

    fun consumeLogout() {
        _logoutState.update { it.copy(loggedOut = false) }
    }

    // ═════════════════════════════════════════════
    // SESSION (Splash screen)
    // ═════════════════════════════════════════════

    /**
     * Called from the Splash screen to check if the user is already logged in.
     * Restores the JWT to RetrofitClient if session exists.
     */
    fun checkSession() {
        viewModelScope.launch {
            _sessionState.update { it.copy(isChecking = true) }

            try {
                sessionManager.restoreSessionSync()

                val loggedIn = sessionManager.isLoggedIn()
                val role = sessionManager.getRole()
                val user = sessionManager.getFullSession()

                _sessionState.update {
                    it.copy(
                        isChecking = false,
                        isLoggedIn = loggedIn,
                        role = role,
                        user = user
                    )
                }

                Log.d(TAG, "Session check: loggedIn=$loggedIn, role=$role")
            } catch (e: Exception) {
                Log.e(TAG, "Session check failed", e)
                _sessionState.update {
                    it.copy(isChecking = false, isLoggedIn = false)
                }
            }
        }
    }

    // ═════════════════════════════════════════════
    // HEALTH CHECK (backend availability)
    // ═════════════════════════════════════════════

    fun checkBackendHealth(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.healthCheck().fold(
                onSuccess = { onResult(true) },
                onFailure = { onResult(false) }
            )
        }
    }
}

// ═════════════════════════════════════════════
// UI STATE CLASSES
// ═════════════════════════════════════════════

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val role: String = "Resident",           // Resident | Staff | Admin
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSucceeded: Boolean = false,
    val loggedInUser: UserData? = null
)

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val role: String = "Resident",
    val communityId: String = "",
    val communityName: String = "",
    val communities: List<CommunityData> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingCommunities: Boolean = false,
    val error: String? = null
)

enum class OtpPurpose {
    REGISTER,
    FORGOT_PASSWORD
}

data class OtpUiState(
    val isVisible: Boolean = false,
    val email: String = "",
    val otp: String = "",
    val purpose: OtpPurpose = OtpPurpose.REGISTER,
    val isLoading: Boolean = false,
    val isVerified: Boolean = false,
    val error: String? = null,
    val expiresInSeconds: Int = 600
)

enum class ForgotStep {
    EMAIL,
    OTP,
    NEW_PASSWORD
}

data class ForgotPasswordUiState(
    val email: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val step: ForgotStep = ForgotStep.EMAIL,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class LogoutUiState(
    val isLoading: Boolean = false,
    val loggedOut: Boolean = false
)

data class SessionUiState(
    val isChecking: Boolean = false,
    val isLoggedIn: Boolean = false,
    val role: String? = null,
    val user: UserData? = null
)
package com.example.myiapp.data.repository

import android.util.Log
import com.example.myiapp.data.local.SessionManager
import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import org.json.JSONObject

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AuthRepository — handles authentication, profile, and session
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • Wraps ApiService calls in Result<T> so callers never crash
 *  • Auto-saves the JWT + user data to SessionManager after login
 *  • Auto-clears session on logout
 *  • Sends the JWT via RetrofitClient's interceptor (no manual token passing)
 */
class AuthRepository(
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    private val api = RetrofitClient.apiService

    // ─────────────────────────────────────────────
    // Helper: Extract a clean error message
    // ─────────────────────────────────────────────
    private fun parseError(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrBlank()) return fallback
        return try {
            val json = JSONObject(errorBody)
            when {
                json.has("message") -> json.getString("message")
                json.has("error") -> json.getString("error")
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun networkError(e: Exception): Exception {
        Log.e(TAG, "Network error: ${e.message}", e)
        return Exception("Cannot connect to server. Please check your network.")
    }

    // ═════════════════════════════════════════════
    // AUTH METHODS
    // ═════════════════════════════════════════════

    /**
     * Login and auto-save session on success.
     *
     * @return Result with AuthResponse on success, Exception on failure.
     *         On success, SessionManager is already populated with
     *         the JWT + user info.
     */
    suspend fun login(
        email: String,
        password: String,
        role: String
    ): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password, role))
            val body = response.body()

            if (response.isSuccessful && body != null && body.success) {
                val token = body.token
                val user = body.user

                if (token.isNullOrBlank() || user == null) {
                    Log.e(TAG, "Login succeeded but token/user missing in response")
                    return Result.failure(Exception("Login failed: malformed server response"))
                }

                // ✅ Auto-save the session + push JWT to RetrofitClient
                sessionManager.saveSession(token = token, user = user)
                Log.d(TAG, "✅ Login success for ${user.email} (${user.role})")

                Result.success(body)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Login failed"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Register a new user. Returns SimpleResponse on success.
     * The user must still verify OTP before login.
     */
    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        communityId: String
    ): Result<SimpleResponse> {
        return try {
            val request = RegisterRequest(
                name = name,
                email = email,
                phone = phone,
                password = password,
                role = role,
                communityId = communityId
            )
            val response = api.register(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Registration failed"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Verify the OTP sent to the user's email after registration.
     */
    suspend fun verifyOtp(email: String, otp: String): Result<SimpleResponse> {
        return try {
            val response = api.verifyOtp(VerifyOtpRequest(email, otp))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "OTP verification failed"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Request a password reset OTP to be sent to the user's email.
     */
    suspend fun forgotPassword(email: String): Result<SimpleResponse> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to send reset OTP"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Reset the user's password using the OTP received via email.
     */
    suspend fun resetPassword(
        email: String,
        otp: String,
        newPassword: String
    ): Result<SimpleResponse> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, otp, newPassword))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Password reset failed"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Logout — clears the local session and pushes null to RetrofitClient.
     * Optionally calls the server's logout endpoint to invalidate server-side state.
     */
    suspend fun logout(): Result<SimpleResponse> {
        return try {
            // Try server-side logout first (best-effort)
            try {
                api.logout()
            } catch (e: Exception) {
                Log.w(TAG, "Server logout failed (ignored): ${e.message}")
            }

            // Always clear local session
            sessionManager.clearSession()
            Log.d(TAG, "✅ Logout complete")
            Result.success(SimpleResponse(success = true, message = "Logged out"))
        } catch (e: Exception) {
            Log.e(TAG, "Logout error: ${e.message}", e)
            // Even on failure, still try to clear local
            sessionManager.clearSession()
            Result.success(SimpleResponse(success = true, message = "Logged out locally"))
        }
    }

    // ═════════════════════════════════════════════
    // USER PROFILE METHODS (JWT auto-injected)
    // ═════════════════════════════════════════════

    suspend fun getProfile(): Result<UserProfileResponse> {
        return try {
            val response = api.getProfile()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load profile"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun updateProfile(
        name: String? = null,
        phone: String? = null,
        avatarUrl: String? = null
    ): Result<UserProfileResponse> {
        return try {
            val request = UpdateProfileRequest(
                name = name,
                phone = phone,
                avatarUrl = avatarUrl
            )
            val response = api.updateProfile(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Sync SessionManager with updated values
                body.user?.let { user ->
                    sessionManager.updateUserInfo(
                        name = user.name,
                        phone = user.phone,
                        avatarUrl = user.avatarUrl
                    )
                }

                Result.success(body)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update profile"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<SimpleResponse> {
        return try {
            val request = ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = newPassword
            )
            val response = api.changePassword(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to change password"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // PUBLIC (no-auth) METHODS
    // ═════════════════════════════════════════════

    /**
     * Fetch the list of active communities for the register dropdown.
     */
    suspend fun getActiveCommunities(): Result<CommunityListResponse> {
        return try {
            val response = api.getActiveCommunities()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load communities"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Health check — used by the Splash screen to test backend reachability.
     */
    suspend fun healthCheck(): Result<HealthResponse> {
        return try {
            val response = api.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Backend unreachable (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // SESSION HELPERS
    // ═════════════════════════════════════════════

    /**
     * True if a JWT token is stored locally.
     * Use this in the Splash screen to decide whether to
     * route to Login or to the role-specific home screen.
     */
    suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    /**
     * Read the currently-saved role from the local session.
     * Returns null if no session exists.
     */
    suspend fun getCurrentRole(): String? = sessionManager.getRole()
}
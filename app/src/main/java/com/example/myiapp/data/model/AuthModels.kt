package com.example.myiapp.data.model

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────
// User data returned from backend
// All fields nullable with defaults so Gson never crashes on partial responses
// ─────────────────────────────────────────────
data class UserData(
    @SerializedName("id")               val id: String = "",
    @SerializedName("name")             val name: String = "",
    @SerializedName("email")            val email: String = "",
    @SerializedName("phone")            val phone: String = "",
    @SerializedName("role")             val role: String = "Resident",
    @SerializedName("avatarUrl")        val avatarUrl: String? = null,
    @SerializedName("isVerified")       val isVerified: Boolean = false,
    @SerializedName("isActive")         val isActive: Boolean = true,
    @SerializedName("communityId")      val communityId: String? = null,
    @SerializedName("communityName")    val communityName: String = "",
    @SerializedName("createdAt")        val createdAt: String? = null
)

// ─────────────────────────────────────────────
// Login / Register response
// ─────────────────────────────────────────────
data class AuthResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("token")    val token: String? = null,
    @SerializedName("user")     val user: UserData? = null
)

// ─────────────────────────────────────────────
// Simple response for verify-otp, forgot-password, reset-password
// ─────────────────────────────────────────────
data class SimpleResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("modifiedCount") val modifiedCount: Int? = null,
    @SerializedName("deletedCount")  val deletedCount: Int? = null
)

// ─────────────────────────────────────────────
// Health response
// ─────────────────────────────────────────────
data class HealthResponse(
    @SerializedName("status")   val status: String = "",
    @SerializedName("version")  val version: String = "",
    @SerializedName("success")  val success: Boolean = true,
    @SerializedName("message")  val message: String? = null
)

// ─────────────────────────────────────────────
// Response for GET /api/users/profile
// ─────────────────────────────────────────────
data class UserProfileResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("user")     val user: UserData? = null
)

// ─────────────────────────────────────────────
// Request bodies (sent to backend)
// ─────────────────────────────────────────────
data class LoginRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("role")     val role: String
)

data class RegisterRequest(
    @SerializedName("name")         val name: String,
    @SerializedName("email")        val email: String,
    @SerializedName("phone")        val phone: String,
    @SerializedName("password")     val password: String,
    @SerializedName("role")         val role: String,
    @SerializedName("communityId")  val communityId: String
)

data class VerifyOtpRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("otp")      val otp: String
)

data class ForgotPasswordRequest(
    @SerializedName("email")    val email: String
)

data class ResetPasswordRequest(
    @SerializedName("email")        val email: String,
    @SerializedName("otp")          val otp: String,
    @SerializedName("newPassword")  val newPassword: String
)

data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

// ─────────────────────────────────────────────
// Update profile request (PUT /api/users/profile)
// All fields optional — only send what changed.
// Email and role are NOT editable.
// Community is NOT editable (Admin must reassign).
// ─────────────────────────────────────────────
data class UpdateProfileRequest(
    @SerializedName("name")         val name: String? = null,
    @SerializedName("phone")        val phone: String? = null,
    @SerializedName("avatarUrl")    val avatarUrl: String? = null
)

// ─────────────────────────────────────────────
// Change password request (PUT /api/users/change-password)
// ─────────────────────────────────────────────
data class ChangePasswordRequest(
    @SerializedName("currentPassword")  val currentPassword: String,
    @SerializedName("newPassword")      val newPassword: String
)

// ═════════════════════════════════════════════
// LOCATION UPDATE (NEW)
// ═════════════════════════════════════════════
// Sent periodically from Android to backend so the admin
// dashboard can map users and auto-assign nearby problems.

data class LocationUpdateRequest(
    @SerializedName("latitude")     val latitude: Double,
    @SerializedName("longitude")    val longitude: Double,
    @SerializedName("accuracy")     val accuracy: Float? = null,
    @SerializedName("timestamp")    val timestamp: Long = System.currentTimeMillis()
)

// ═════════════════════════════════════════════
// MAINTAINERS LIST
// ═════════════════════════════════════════════
// Used by Admin dashboard + "assign problem" screens.

data class MaintainerData(
    @SerializedName("id")                val id: String = "",
    @SerializedName("name")              val name: String = "",
    @SerializedName("email")             val email: String = "",
    @SerializedName("phone")             val phone: String = "",
    @SerializedName("role")              val role: String = "Staff",
    @SerializedName("avatarUrl")         val avatarUrl: String? = null,
    @SerializedName("isActive")          val isActive: Boolean = true,
    @SerializedName("isVerified")        val isVerified: Boolean = false,
    @SerializedName("communityId")       val communityId: String? = null,
    @SerializedName("communityName")     val communityName: String = "",
    @SerializedName("stats")             val stats: MaintainerStatsData? = null
)

data class MaintainerStatsData(
    @SerializedName("activeTasks")      val activeTasks: Int = 0,
    @SerializedName("resolvedTasks")    val resolvedTasks: Int = 0,
    @SerializedName("totalTasks")       val totalTasks: Int = 0,
    @SerializedName("completionRate")   val completionRate: Int = 0,
    @SerializedName("avgAssignHours")   val avgAssignHours: Double = 0.0,
    @SerializedName("avgResolveHours")  val avgResolveHours: Double = 0.0,
    @SerializedName("avgRating")        val avgRating: Double = 0.0,
    @SerializedName("totalRatings")     val totalRatings: Int = 0,
    @SerializedName("verifiedCount")    val verifiedCount: Int = 0
)

data class MaintainersResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("count")        val count: Int = 0,
    @SerializedName("maintainers")  val maintainers: List<MaintainerData> = emptyList()
)

// ═════════════════════════════════════════════
// COMMUNITY MODELS
// ═════════════════════════════════════════════
// Used by Register screen (community dropdown) and
// SuperAdmin community management screens.

data class Community(
    @SerializedName("id")               val id: String = "",
    @SerializedName("name")             val name: String = "",
    @SerializedName("slug")             val slug: String = "",
    @SerializedName("description")      val description: String = "",
    @SerializedName("address")          val address: String = "",
    @SerializedName("contactEmail")     val contactEmail: String = "",
    @SerializedName("contactPhone")     val contactPhone: String = "",
    @SerializedName("logoUrl")          val logoUrl: String = "",
    @SerializedName("color")            val color: String = "#10B981",
    @SerializedName("isActive")         val isActive: Boolean = true,
    @SerializedName("memberCount")      val memberCount: Int = 0,
    @SerializedName("problemCount")     val problemCount: Int = 0,
    @SerializedName("resolvedCount")    val resolvedCount: Int = 0,
    @SerializedName("createdByName")    val createdByName: String = "",
    @SerializedName("createdAt")        val createdAt: String? = null
)

typealias CommunityData = Community

data class CommunityListResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("count")        val count: Int = 0,
    @SerializedName("communities")  val communities: List<Community> = emptyList()
)

data class SingleCommunityResponse(
    @SerializedName("success")    val success: Boolean = false,
    @SerializedName("message")    val message: String? = null,
    @SerializedName("community")  val community: Community? = null
)

data class CreateCommunityRequest(
    @SerializedName("name")          val name: String,
    @SerializedName("description")   val description: String? = null,
    @SerializedName("address")       val address: String? = null,
    @SerializedName("contactEmail")  val contactEmail: String? = null,
    @SerializedName("contactPhone")  val contactPhone: String? = null,
    @SerializedName("color")         val color: String? = null
)

data class UpdateCommunityRequest(
    @SerializedName("name")          val name: String? = null,
    @SerializedName("description")   val description: String? = null,
    @SerializedName("address")       val address: String? = null,
    @SerializedName("contactEmail")  val contactEmail: String? = null,
    @SerializedName("contactPhone")  val contactPhone: String? = null,
    @SerializedName("color")         val color: String? = null,
    @SerializedName("isActive")      val isActive: Boolean? = null
)

// ═════════════════════════════════════════════
// ADMIN CONFIG MODELS
// ═════════════════════════════════════════════
// For SuperAdmin to update SMTP, Gemini, YOLO, etc.

data class ConfigEntry(
    @SerializedName("key")           val key: String = "",
    @SerializedName("value")         val value: Map<String, Any>? = null,
    @SerializedName("description")   val description: String = "",
    @SerializedName("isSecret")      val isSecret: Boolean = false,
    @SerializedName("updatedByName") val updatedByName: String = "",
    @SerializedName("updatedAt")     val updatedAt: String? = null
)

data class ConfigListResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("configs")  val configs: List<ConfigEntry> = emptyList()
)

data class UpdateConfigRequest(
    @SerializedName("key")          val key: String,
    @SerializedName("value")        val value: Map<String, Any>,
    @SerializedName("description")  val description: String? = null
)

data class TestSmtpRequest(
    @SerializedName("email")    val email: String
)

// ═════════════════════════════════════════════
// NOTIFICATION BULK READ
// ═════════════════════════════════════════════

data class MarkBulkReadRequest(
    @SerializedName("ids")  val ids: List<String>
)
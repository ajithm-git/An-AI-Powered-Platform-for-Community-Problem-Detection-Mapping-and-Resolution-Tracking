package com.example.myiapp.data.repository

import android.util.Log
import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import org.json.JSONObject

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AdminRepository — wraps all admin-only API calls
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Covers:
 *  • Dashboard stats
 *  • User CRUD + role promotion
 *  • Maintainer stats + detail
 *  • Problem reassignment + verification override
 *  • Verification detail (YOLO, timing, notification flags)
 *  • Full problem edit + create-on-behalf
 *  • Community CRUD (get, create, update, toggle, delete)
 *  • Config management (SMTP, Gemini, YOLO) — SuperAdmin
 *
 *  All methods return Result<T> — callers never catch exceptions.
 *  JWT is auto-injected by RetrofitClient.authInterceptor.
 */
class AdminRepository {

    companion object {
        private const val TAG = "AdminRepository"
    }

    private val api = RetrofitClient.apiService

    // ─────────────────────────────────────────────
    // Helpers
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
    // DASHBOARD STATS
    // ═════════════════════════════════════════════

    suspend fun getDashboard(communityId: String? = null): Result<AdminDashboardResponse> {
        return try {
            val response = api.getAdminDashboard(communityId?.takeIf { it.isNotBlank() })
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load dashboard"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // USER MANAGEMENT
    // ═════════════════════════════════════════════

    suspend fun getUsers(
        role: String? = null,
        search: String? = null,
        active: Boolean? = null,
        communityId: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<AdminUserListResponse> {
        return try {
            val response = api.getAdminUsers(
                role = role?.takeIf { it.isNotBlank() },
                search = search?.takeIf { it.isNotBlank() },
                active = active,
                communityId = communityId?.takeIf { it.isNotBlank() },
                limit = limit,
                skip = skip
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load users"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /** Search users by name/email/phone. */
    suspend fun searchUsers(
        query: String,
        role: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<AdminUserListResponse> = getUsers(
        role = role,
        search = query,
        limit = limit,
        skip = skip
    )

    suspend fun createUser(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        communityId: String? = null
    ): Result<AdminUserResponse> {
        return try {
            val request = AdminCreateUserRequest(
                name = name,
                email = email,
                phone = phone,
                password = password,
                role = role,
                communityId = communityId?.takeIf { it.isNotBlank() }
            )
            val response = api.adminCreateUser(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to create user"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun updateUser(
        userId: String,
        name: String? = null,
        phone: String? = null,
        role: String? = null,
        communityId: String? = null,
        isActive: Boolean? = null,
        avatarUrl: String? = null
    ): Result<AdminUserResponse> {
        return try {
            val request = AdminUpdateUserRequest(
                name = name?.takeIf { it.isNotBlank() },
                phone = phone?.takeIf { it.isNotBlank() },
                role = role?.takeIf { it.isNotBlank() },
                communityId = communityId?.takeIf { it.isNotBlank() },
                isActive = isActive,
                avatarUrl = avatarUrl
            )
            val response = api.adminUpdateUser(userId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update user"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun deleteUser(userId: String): Result<SimpleResponse> {
        return try {
            val response = api.adminDeleteUser(userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to delete user"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun promoteUser(
        userId: String,
        newRole: String
    ): Result<AdminUserResponse> {
        return try {
            val response = api.adminPromoteUser(userId, PromoteUserRequest(newRole))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to change role"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // MAINTAINER STATS
    // ═════════════════════════════════════════════

    suspend fun getMaintainers(communityId: String? = null): Result<AdminMaintainersResponse> {
        return try {
            val response = api.getAdminMaintainers(communityId?.takeIf { it.isNotBlank() })
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load maintainers"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun getMaintainerDetail(maintainerId: String): Result<AdminMaintainerDetailResponse> {
        return try {
            val response = api.getAdminMaintainerDetail(maintainerId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load maintainer detail"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // PROBLEM ACTIONS
    // ═════════════════════════════════════════════

    suspend fun reassignProblem(
        problemId: String,
        maintainerId: String,
        reason: String? = null
    ): Result<ProblemResponse> {
        return try {
            val request = ReassignProblemRequest(
                maintainerId = maintainerId,
                reason = reason?.takeIf { it.isNotBlank() }
            )
            val response = api.adminReassignProblem(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to reassign problem"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun overrideVerification(
        problemId: String,
        verdict: String,
        note: String? = null
    ): Result<ProblemResponse> {
        return try {
            val request = OverrideVerificationRequest(
                verdict = verdict,
                note = note?.takeIf { it.isNotBlank() }
            )
            val response = api.adminOverrideVerification(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to override verification"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Rich verification payload for the admin verification screen.
     * Includes before/after images, YOLO detections, verdict + override state,
     * timing metrics, resident notification flags, checklist progress.
     */
    suspend fun getProblemVerification(
        problemId: String
    ): Result<AdminProblemVerificationResponse> {
        return try {
            val response = api.getAdminProblemVerification(problemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load verification detail"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // ADMIN PROBLEM EDITS
    // ═════════════════════════════════════════════

    suspend fun createProblemOnBehalf(
        reporterId: String,
        title: String,
        category: String,
        location: String,
        description: String,
        priority: String = "Medium",
        beforeImage: String? = null
    ): Result<ProblemResponse> {
        return try {
            val request = CreateProblemOnBehalfRequest(
                reporterId = reporterId,
                title = title,
                category = category,
                location = location,
                description = description,
                priority = priority,
                beforeImage = beforeImage
            )
            val response = api.createProblemOnBehalf(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to create problem"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun adminEditProblem(
        problemId: String,
        title: String? = null,
        description: String? = null,
        location: String? = null,
        category: String? = null,
        priority: String? = null,
        status: String? = null,
        assignedTo: String? = null,
        beforeImage: String? = null,
        afterImage: String? = null,
        feedbackStatus: String? = null
    ): Result<ProblemResponse> {
        return try {
            val request = AdminEditProblemRequest(
                title = title,
                description = description,
                location = location,
                category = category,
                priority = priority,
                status = status,
                assignedTo = assignedTo,
                beforeImage = beforeImage,
                afterImage = afterImage,
                feedbackStatus = feedbackStatus
            )
            val response = api.adminEditProblem(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update problem"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // COMMUNITY  ★ NEW — these were missing
    // ═════════════════════════════════════════════

    /**
     * Fetch ALL communities (SuperAdmin only) — includes inactive ones.
     * Used by:
     *   • AdminUserEditScreen (community dropdown for SuperAdmin)
     *   • AdminHomeScreen → Regions tab
     */
    suspend fun getAllCommunitiesAdmin(): Result<CommunityListResponse> {
        return try {
            val response = api.getAllCommunitiesAdmin()
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
     * Fetch ACTIVE communities (public) — used in register dropdowns.
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
     * SuperAdmin — fetch a single community's detailed stats + settings.
     */
    suspend fun getCommunityDetail(communityId: String): Result<SingleCommunityResponse> {
        return try {
            val response = api.getCommunity(communityId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load community detail"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Get the admin-scoped settings for a community.
     */
    suspend fun getCommunitySettings(
        communityId: String? = null
    ): Result<SingleCommunityResponse> {
        return try {
            val response = api.getAdminCommunitySettings(communityId?.takeIf { it.isNotBlank() })
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load community settings"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Create a new community (SuperAdmin only).
     */
    suspend fun createCommunity(
        name: String,
        description: String? = null,
        address: String? = null,
        contactEmail: String? = null,
        color: String? = null
    ): Result<SingleCommunityResponse> {
        return try {
            val request = CreateCommunityRequest(
                name = name,
                description = description,
                address = address,
                contactEmail = contactEmail,
                color = color
            )
            val response = api.createCommunity(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to create community"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Update an existing community (SuperAdmin only).
     */
    suspend fun updateCommunity(
        communityId: String,
        name: String? = null,
        description: String? = null,
        address: String? = null,
        contactEmail: String? = null,
        color: String? = null,
        isActive: Boolean? = null
    ): Result<SingleCommunityResponse> {
        return try {
            val request = UpdateCommunityRequest(
                name = name,
                description = description,
                address = address,
                contactEmail = contactEmail,
                color = color,
                isActive = isActive
            )
            val response = api.updateCommunity(communityId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update community"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Toggle a community active/inactive (SuperAdmin only).
     */
    suspend fun toggleCommunity(communityId: String): Result<SingleCommunityResponse> {
        return try {
            val response = api.toggleCommunity(communityId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to toggle community"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Soft-delete a community (SuperAdmin only).
     * Backend blocks deletion if there are active members.
     */
    suspend fun deleteCommunity(communityId: String): Result<SimpleResponse> {
        return try {
            val response = api.deleteCommunity(communityId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to delete community"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // CONFIG MANAGEMENT (SuperAdmin only)
    // ═════════════════════════════════════════════

    /**
     * Fetch all config keys (SMTP, Gemini, YOLO, etc.).
     * Secrets are masked in the response.
     */
    suspend fun getAllConfig(): Result<ConfigListResponse> {
        return try {
            val response = api.getAllConfig()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load config"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Update a config value in real-time.
     * If SMTP is updated, the backend clears the email transporter cache automatically.
     */
    suspend fun updateConfig(
        key: String,
        value: Map<String, Any>,
        description: String? = null
    ): Result<SimpleResponse> {
        return try {
            val request = UpdateConfigRequest(
                key = key,
                value = value,
                description = description
            )
            val response = api.updateConfig(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update config"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Test SMTP config by sending a test email to the given address.
     */
    suspend fun testSmtpConfig(email: String): Result<SimpleResponse> {
        return try {
            val response = api.testSmtpConfig(TestSmtpRequest(email))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "SMTP test failed"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }
}
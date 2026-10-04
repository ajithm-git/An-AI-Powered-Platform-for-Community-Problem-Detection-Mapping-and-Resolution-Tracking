package com.example.myiapp.data.repository

import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import org.json.JSONObject

class CommunityRepository {

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
        } catch (e: Exception) {
            fallback
        }
    }

    // ═════════════════════════════════════════════
    // PUBLIC — used by Register screen
    // ═════════════════════════════════════════════

    // ─────────────────────────────────────────────
    // List ACTIVE communities (register dropdown)
    // GET /api/communities
    // ─────────────────────────────────────────────
    suspend fun getActiveCommunities(): Result<CommunityListResponse> {
        return try {
            val response = api.getActiveCommunities()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to load communities")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Get single community by ID or slug
    // GET /api/communities/{idOrSlug}
    // ─────────────────────────────────────────────
    suspend fun getCommunity(idOrSlug: String): Result<SingleCommunityResponse> {
        return try {
            val response = api.getCommunity(idOrSlug)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Community not found")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ═════════════════════════════════════════════
    // SUPERADMIN — manage communities
    // ═════════════════════════════════════════════

    // ─────────────────────────────────────────────
    // List ALL communities with member counts
    // GET /api/communities/admin/all
    // ─────────────────────────────────────────────
    suspend fun getAllCommunitiesAdmin(): Result<CommunityListResponse> {
        return try {
            val response = api.getAllCommunitiesAdmin()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to load communities")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Create a new community
    // POST /api/communities
    // ─────────────────────────────────────────────
    suspend fun createCommunity(
        token: String,
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
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to create community")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Update an existing community
    // PUT /api/communities/{id}
    // All fields optional — only send what changed
    // ─────────────────────────────────────────────
    suspend fun updateCommunity(
        token: String,
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
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to update community")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Toggle community active/inactive
    // PATCH /api/communities/{id}/toggle
    // ─────────────────────────────────────────────
    suspend fun toggleCommunity(
        token: String,
        communityId: String
    ): Result<SingleCommunityResponse> {
        return try {
            val response = api.toggleCommunity(communityId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to toggle community")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }

    // ─────────────────────────────────────────────
    // Soft delete a community
    // DELETE /api/communities/{id}
    // ─────────────────────────────────────────────
    suspend fun deleteCommunity(
        token: String,
        communityId: String
    ): Result<SimpleResponse> {
        return try {
            val response = api.deleteCommunity(communityId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response.errorBody()?.string(), "Failed to delete community")))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Cannot connect to server. Please check your network."))
        }
    }
}
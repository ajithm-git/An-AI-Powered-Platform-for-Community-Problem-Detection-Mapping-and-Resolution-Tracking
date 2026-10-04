package com.example.myiapp.data.repository

import android.util.Base64
import android.util.Log
import com.example.myiapp.data.model.*
import com.example.myiapp.data.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ProblemRepository — wraps all problem-related API calls
 * ═══════════════════════════════════════════════════════════════════
 *
 *  • Auto-JWT via RetrofitClient.authInterceptor (no token params)
 *  • All methods return Result<T> — callers never catch exceptions
 *  • Covers: create, feed, detail, likes, comments, verify, feedback,
 *    work checklist, mark-viewed, maintainer dashboard, my-work,
 *    admin: create-on-behalf, edit, reassign, override, delete
 */
class ProblemRepository {

    companion object {
        private const val TAG = "ProblemRepository"
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
    // AI ANALYSIS
    // ═════════════════════════════════════════════

    /**
     * Analyze a problem image (YOLO + Gemini draft).
     * Called before the user submits the final problem.
     */
    suspend fun analyzeProblem(
        beforeImage: String,
        locationHint: String? = null
    ): Result<AnalyzeProblemResponse> {
        return try {
            val request = AnalyzeProblemRequest(
                beforeImage = beforeImage,
                locationHint = locationHint?.takeIf { it.isNotBlank() }
            )
            val response = api.analyzeProblem(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to analyze image"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Convenience overload — pass raw bytes, auto-encodes to base64.
     */
    suspend fun analyzeProblemFromBytes(
        imageBytes: ByteArray,
        locationHint: String? = null
    ): Result<AnalyzeProblemResponse> {
        val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        return analyzeProblem("data:image/jpeg;base64,$base64", locationHint)
    }

    // ═════════════════════════════════════════════
    // PROBLEM CRUD
    // ═════════════════════════════════════════════

    suspend fun createProblem(
        title: String,
        category: String,
        location: String,
        description: String,
        priority: String = "Medium",
        beforeImage: String? = null
    ): Result<ProblemResponse> {
        return try {
            val request = CreateProblemRequest(
                title = title,
                category = category,
                location = location,
                description = description,
                priority = priority,
                beforeImage = beforeImage
            )
            val response = api.createProblem(request)
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

    /**
     * Multipart image upload — alternative to base64 JSON.
     * Currently backend only exposes `/api/problems` (JSON), so this
     * is provided for future compatibility with `/api/problems/with-images`.
     */
    suspend fun createProblemWithImages(
        problemDataJson: String,
        imageBytesList: List<ByteArray>
    ): Result<ProblemResponse> {
        return try {
            val dataPart = problemDataJson
                .toRequestBody("application/json".toMediaTypeOrNull())

            val imageParts = imageBytesList.mapIndexed { index, bytes ->
                val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("images[$index]", "image_$index.jpg", body)
            }

            val response = api.createProblemWithImages(dataPart, imageParts)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to create problem with images"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Community feed (paginated).
     */
    suspend fun getProblems(
        limit: Int = 30,
        skip: Int = 0,
        communityId: String? = null,
        status: String? = null,
        category: String? = null
    ): Result<ProblemListResponse> {
        return try {
            val response = api.getProblems(
                limit = limit,
                skip = skip,
                communityId = communityId?.takeIf { it.isNotBlank() },
                status = status?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() }
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load problems"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /** Filter by category only. */
    suspend fun getProblemsByCategory(
        category: String,
        limit: Int = 30,
        skip: Int = 0
    ): Result<ProblemListResponse> = getProblems(limit = limit, skip = skip, category = category)

    /** Filter by status only. */
    suspend fun getProblemsByStatus(
        status: String,
        limit: Int = 30,
        skip: Int = 0
    ): Result<ProblemListResponse> = getProblems(limit = limit, skip = skip, status = status)

    /** Resident's own reports. */
    suspend fun getMyProblems(): Result<ProblemListResponse> {
        return try {
            val response = api.getMyProblems()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load your reports"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /** Problems assigned to the current maintainer. */
    suspend fun getAssignedProblems(): Result<ProblemListResponse> {
        return try {
            val response = api.getAssignedProblems()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load assigned tasks"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /** Single problem detail. */
    suspend fun getProblemById(problemId: String): Result<ProblemResponse> {
        return try {
            val response = api.getProblemById(problemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load problem"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * Partial update (reporter or admin).
     * For a full admin edit, use [adminEditProblem].
     */
    suspend fun updateProblem(
        problemId: String,
        title: String? = null,
        category: String? = null,
        location: String? = null,
        description: String? = null,
        priority: String? = null
    ): Result<ProblemResponse> {
        return try {
            val request = UpdateProblemRequest(
                title = title,
                category = category,
                location = location,
                description = description,
                priority = priority
            )
            val response = api.updateProblem(problemId, request)
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
    // INTERACTIONS
    // ═════════════════════════════════════════════

    suspend fun toggleLike(problemId: String): Result<LikeResponse> {
        return try {
            val response = api.toggleLike(problemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to toggle like"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun addComment(
        problemId: String,
        content: String
    ): Result<CommentResponse> {
        return try {
            val response = api.addComment(problemId, AddCommentRequest(content))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to add comment"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // ASSIGNMENT / STATUS
    // ═════════════════════════════════════════════

    suspend fun assignProblem(
        problemId: String,
        maintainerId: String
    ): Result<ProblemResponse> {
        return try {
            val response = api.assignProblem(problemId, AssignProblemRequest(maintainerId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to assign"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun updateProblemStatus(
        problemId: String,
        status: String? = null,
        note: String? = null,
        afterImage: String? = null
    ): Result<ProblemResponse> {
        return try {
            val response = api.updateProblemStatus(
                problemId,
                UpdateStatusRequest(status, note, afterImage)
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update status"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    suspend fun deleteProblem(problemId: String): Result<SimpleResponse> {
        return try {
            val response = api.deleteProblem(problemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to delete problem"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // MAINTAINER DASHBOARD
    // ═════════════════════════════════════════════

    suspend fun getMaintainerDashboard(): Result<MaintainerDashboardResponse> {
        return try {
            val response = api.getMaintainerDashboard()
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

    /**
     * Maintainer home — priority-sorted notFinished + finished lists.
     */
    suspend fun getMyWork(): Result<MyWorkResponse> {
        return try {
            val response = api.getMyWork()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load your work list"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // VERIFY RESOLUTION (maintainer)
    // ═════════════════════════════════════════════

    suspend fun verifyResolution(
        problemId: String,
        afterImage: String,
        note: String? = null
    ): Result<VerifyResolutionResponse> {
        return try {
            val request = VerifyResolutionRequest(
                afterImage = afterImage,
                note = note?.takeIf { it.isNotBlank() }
            )
            val response = api.verifyResolution(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to verify resolution"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // FEEDBACK (resident)
    // ═════════════════════════════════════════════

    suspend fun submitFeedback(
        problemId: String,
        rating: Int,
        comment: String? = null
    ): Result<SubmitFeedbackResponse> {
        return try {
            val request = SubmitFeedbackRequest(
                rating = rating,
                comment = comment?.takeIf { it.isNotBlank() }
            )
            val response = api.submitFeedback(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to submit feedback"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // LIST MAINTAINERS (admin assignment)
    // ═════════════════════════════════════════════

    suspend fun getMaintainers(
        communityId: String? = null
    ): Result<MaintainersResponse> {
        return try {
            val response = api.getMaintainers(communityId?.takeIf { it.isNotBlank() })
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

    // ═════════════════════════════════════════════
    // WORK CHECKLIST
    // ═════════════════════════════════════════════

    /**
     * GET /api/problems/{id}/work-checklist
     * Returns cached checklist or AI-generates one if not present.
     */
    suspend fun getWorkChecklist(problemId: String): Result<WorkChecklistResponse> {
        return try {
            val response = api.getWorkChecklist(problemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to load work checklist"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    /**
     * PATCH /api/problems/{id}/work-checklist/step
     * Toggle a single step's completion state.
     */
    suspend fun toggleChecklistStep(
        problemId: String,
        stepId: String,
        isCompleted: Boolean
    ): Result<WorkChecklistResponse> {
        return try {
            val request = ToggleChecklistStepRequest(stepId = stepId, isCompleted = isCompleted)
            val response = api.toggleChecklistStep(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to update step"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // RESIDENT VIEW TRACKING (Q4 gate)
    // ═════════════════════════════════════════════

    /**
     * Called automatically when a resident opens the detail screen of a Resolved problem.
     * Sets residentViewedAt — required before feedback can be submitted.
     */
    suspend fun markProblemViewed(problemId: String): Result<MarkViewedResponse> {
        return try {
            val response = api.markProblemViewed(problemId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to mark as viewed"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // ADMIN: FULL VERIFICATION DETAIL
    // ═════════════════════════════════════════════

    /**
     * Rich verification payload for the admin verification screen.
     */
    suspend fun getAdminProblemVerification(
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
    // ADMIN: COMMUNITY SETTINGS
    // ═════════════════════════════════════════════

    suspend fun getAdminCommunitySettings(
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

    // ═════════════════════════════════════════════
    // ADMIN: CREATE ON BEHALF
    // ═════════════════════════════════════════════

    suspend fun createProblemOnBehalf(
        request: CreateProblemOnBehalfRequest
    ): Result<ProblemResponse> {
        return try {
            val response = api.createProblemOnBehalf(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to create problem on behalf"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // ADMIN: FULL EDIT
    // ═════════════════════════════════════════════

    suspend fun adminEditProblem(
        problemId: String,
        request: AdminEditProblemRequest
    ): Result<ProblemResponse> {
        return try {
            val response = api.adminEditProblem(problemId, request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(
                    Exception(parseError(response.errorBody()?.string(), "Failed to edit problem"))
                )
            }
        } catch (e: Exception) {
            Result.failure(networkError(e))
        }
    }

    // ═════════════════════════════════════════════
    // ADMIN: REASSIGN
    // ═════════════════════════════════════════════

    suspend fun adminReassignProblem(
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

    // ═════════════════════════════════════════════
    // ADMIN: OVERRIDE VERIFICATION
    // ═════════════════════════════════════════════

    /**
     * verdict: VERIFIED | PARTIAL | NOT_VERIFIED | UNVERIFIABLE
     */
    suspend fun adminOverrideVerification(
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
}
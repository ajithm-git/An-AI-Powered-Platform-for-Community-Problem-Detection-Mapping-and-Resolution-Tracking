package com.example.myiapp.data.network

import com.example.myiapp.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ApiService — Retrofit interface for MyAIApp backend
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Base URL is configured in NetworkModule:
 *      http://10.0.2.2:5000/         (dev, Android emulator)
 *
 *  IMPORTANT:
 *  ----------
 *  • Every path here is relative — the "api/" prefix is included
 *    so the final URL becomes http://10.0.2.2:5000/api/...
 *
 *  • DO NOT add @Header("Authorization") to methods.
 *    The AuthInterceptor (see AuthInterceptor.kt) automatically
 *    injects "Authorization: Bearer <jwt>" for every request
 *    that isn't explicitly marked @NoAuth.
 *
 *  • Endpoints that must skip auth (login, register, ...) are
 *    annotated with @Headers("No-Auth: true") — the interceptor
 *    reads that header and skips token injection.
 * ═══════════════════════════════════════════════════════════════════
 */
interface ApiService {

    // ═════════════════════════════════════════════
    // HEALTH CHECK
    // ═════════════════════════════════════════════

    @GET("api/health")
    @Headers("No-Auth: true")
    suspend fun healthCheck(): Response<HealthResponse>

    // ═════════════════════════════════════════════
    // AUTH ROUTES  (/api/auth/*)
    // ═════════════════════════════════════════════

    @POST("api/auth/login")
    @Headers("No-Auth: true")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    @Headers("No-Auth: true")
    suspend fun register(@Body request: RegisterRequest): Response<SimpleResponse>

    @POST("api/auth/verify-otp")
    @Headers("No-Auth: true")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<SimpleResponse>

    @POST("api/auth/forgot-password")
    @Headers("No-Auth: true")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<SimpleResponse>

    @POST("api/auth/reset-password")
    @Headers("No-Auth: true")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<SimpleResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<SimpleResponse>

    // ═════════════════════════════════════════════
    // USER ROUTES  (/api/users/*)  —  require JWT
    // ═════════════════════════════════════════════

    @GET("api/users/profile")
    suspend fun getProfile(): Response<UserProfileResponse>

    @PUT("api/users/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfileResponse>

    @PUT("api/users/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<SimpleResponse>

    @GET("api/users/maintainers")
    suspend fun getMaintainers(
        @Query("communityId") communityId: String? = null
    ): Response<MaintainersResponse>

    // ─────────────────────────────────────────────
    // Avatar upload (multipart)
    // ─────────────────────────────────────────────
    @Multipart
    @POST("api/users/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<UserProfileResponse>

    // ═════════════════════════════════════════════
    // LOCATION UPDATE (NEW)
    // ═════════════════════════════════════════════
    // Called periodically from the Android app to update the
    // user's current GPS location. Useful for:
    //   • Auto-assigning nearby problems
    //   • Mapping problems on admin dashboard
    //   • Showing "who's nearby" in emergencies

    @POST("api/users/location")
    suspend fun updateLocation(
        @Body request: LocationUpdateRequest
    ): Response<SimpleResponse>

    // ═════════════════════════════════════════════
    // COMMUNITY ROUTES  (/api/communities/*)
    // ═════════════════════════════════════════════

    @GET("api/communities")
    @Headers("No-Auth: true")
    suspend fun getActiveCommunities(): Response<CommunityListResponse>

    @GET("api/communities/{idOrSlug}")
    @Headers("No-Auth: true")
    suspend fun getCommunity(
        @Path("idOrSlug") idOrSlug: String
    ): Response<SingleCommunityResponse>

    @GET("api/communities/admin/all")
    suspend fun getAllCommunitiesAdmin(): Response<CommunityListResponse>

    @POST("api/communities")
    suspend fun createCommunity(
        @Body request: CreateCommunityRequest
    ): Response<SingleCommunityResponse>

    @PUT("api/communities/{id}")
    suspend fun updateCommunity(
        @Path("id") communityId: String,
        @Body request: UpdateCommunityRequest
    ): Response<SingleCommunityResponse>

    @PATCH("api/communities/{id}/toggle")
    suspend fun toggleCommunity(
        @Path("id") communityId: String
    ): Response<SingleCommunityResponse>

    @DELETE("api/communities/{id}")
    suspend fun deleteCommunity(
        @Path("id") communityId: String
    ): Response<SimpleResponse>

    // ═════════════════════════════════════════════
    // PROBLEM ROUTES  (/api/problems/*)
    // ═════════════════════════════════════════════

    // ── AI Analyze (long-running) ──
    @Streaming
    @POST("api/problems/analyze")
    suspend fun analyzeProblem(
        @Body request: AnalyzeProblemRequest
    ): Response<AnalyzeProblemResponse>

    @POST("api/problems")
    suspend fun createProblem(
        @Body request: CreateProblemRequest
    ): Response<ProblemResponse>

    // ── Multipart version (with image upload) ──
    @Multipart
    @POST("api/problems/with-images")
    suspend fun createProblemWithImages(
        @Part("data") data: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ProblemResponse>

    @GET("api/problems")
    suspend fun getProblems(
        @Query("limit") limit: Int = 30,
        @Query("skip") skip: Int = 0,
        @Query("communityId") communityId: String? = null,
        @Query("status") status: String? = null,
        @Query("category") category: String? = null
    ): Response<ProblemListResponse>

    @GET("api/problems/mine")
    suspend fun getMyProblems(): Response<ProblemListResponse>

    @GET("api/problems/assigned-to-me")
    suspend fun getAssignedProblems(): Response<ProblemListResponse>

    @GET("api/problems/maintainer-dashboard")
    suspend fun getMaintainerDashboard(): Response<MaintainerDashboardResponse>

    @GET("api/problems/my-work")
    suspend fun getMyWork(): Response<MyWorkResponse>

    @GET("api/problems/{id}")
    suspend fun getProblemById(
        @Path("id") problemId: String
    ): Response<ProblemResponse>

    @PUT("api/problems/{id}")
    suspend fun updateProblem(
        @Path("id") problemId: String,
        @Body request: UpdateProblemRequest
    ): Response<ProblemResponse>

    @POST("api/problems/{id}/like")
    suspend fun toggleLike(
        @Path("id") problemId: String
    ): Response<LikeResponse>

    @POST("api/problems/{id}/comment")
    suspend fun addComment(
        @Path("id") problemId: String,
        @Body request: AddCommentRequest
    ): Response<CommentResponse>

    @PATCH("api/problems/{id}/verify")
    suspend fun verifyResolution(
        @Path("id") problemId: String,
        @Body request: VerifyResolutionRequest
    ): Response<VerifyResolutionResponse>

    @POST("api/problems/{id}/feedback")
    suspend fun submitFeedback(
        @Path("id") problemId: String,
        @Body request: SubmitFeedbackRequest
    ): Response<SubmitFeedbackResponse>

    @GET("api/problems/{id}/work-checklist")
    suspend fun getWorkChecklist(
        @Path("id") problemId: String
    ): Response<WorkChecklistResponse>

    @PATCH("api/problems/{id}/work-checklist/step")
    suspend fun toggleChecklistStep(
        @Path("id") problemId: String,
        @Body request: ToggleChecklistStepRequest
    ): Response<WorkChecklistResponse>

    @POST("api/problems/{id}/mark-viewed")
    suspend fun markProblemViewed(
        @Path("id") problemId: String
    ): Response<MarkViewedResponse>

    @PATCH("api/problems/{id}/assign")
    suspend fun assignProblem(
        @Path("id") problemId: String,
        @Body request: AssignProblemRequest
    ): Response<ProblemResponse>

    @PATCH("api/problems/{id}/status")
    suspend fun updateProblemStatus(
        @Path("id") problemId: String,
        @Body request: UpdateStatusRequest
    ): Response<ProblemResponse>

    @DELETE("api/problems/{id}")
    suspend fun deleteProblem(
        @Path("id") problemId: String
    ): Response<SimpleResponse>

    // ── Admin: on-behalf + edit ──
    @POST("api/problems/admin/create-on-behalf")
    suspend fun createProblemOnBehalf(
        @Body request: CreateProblemOnBehalfRequest
    ): Response<ProblemResponse>

    @PUT("api/problems/admin/{id}/edit")
    suspend fun adminEditProblem(
        @Path("id") problemId: String,
        @Body request: AdminEditProblemRequest
    ): Response<ProblemResponse>

    // ═════════════════════════════════════════════
    // ADMIN ROUTES  (/api/admin/*)
    // ═════════════════════════════════════════════

    @GET("api/admin/dashboard")
    suspend fun getAdminDashboard(
        @Query("communityId") communityId: String? = null
    ): Response<AdminDashboardResponse>

    @GET("api/admin/community")
    suspend fun getAdminCommunitySettings(
        @Query("communityId") communityId: String? = null
    ): Response<SingleCommunityResponse>

    @GET("api/admin/users")
    suspend fun getAdminUsers(
        @Query("role") role: String? = null,
        @Query("search") search: String? = null,
        @Query("active") active: Boolean? = null,
        @Query("communityId") communityId: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<AdminUserListResponse>

    @POST("api/admin/users")
    suspend fun adminCreateUser(
        @Body request: AdminCreateUserRequest
    ): Response<AdminUserResponse>

    @PUT("api/admin/users/{id}")
    suspend fun adminUpdateUser(
        @Path("id") userId: String,
        @Body request: AdminUpdateUserRequest
    ): Response<AdminUserResponse>

    @DELETE("api/admin/users/{id}")
    suspend fun adminDeleteUser(
        @Path("id") userId: String
    ): Response<SimpleResponse>

    @PATCH("api/admin/users/{id}/role")
    suspend fun adminPromoteUser(
        @Path("id") userId: String,
        @Body request: PromoteUserRequest
    ): Response<AdminUserResponse>

    @GET("api/admin/maintainers")
    suspend fun getAdminMaintainers(
        @Query("communityId") communityId: String? = null
    ): Response<AdminMaintainersResponse>

    @GET("api/admin/maintainers/{id}")
    suspend fun getAdminMaintainerDetail(
        @Path("id") maintainerId: String
    ): Response<AdminMaintainerDetailResponse>

    @GET("api/admin/problems/{id}/verification")
    suspend fun getAdminProblemVerification(
        @Path("id") problemId: String
    ): Response<AdminProblemVerificationResponse>

    @PATCH("api/admin/problems/{id}/reassign")
    suspend fun adminReassignProblem(
        @Path("id") problemId: String,
        @Body request: ReassignProblemRequest
    ): Response<ProblemResponse>

    @PATCH("api/admin/problems/{id}/override-verification")
    suspend fun adminOverrideVerification(
        @Path("id") problemId: String,
        @Body request: OverrideVerificationRequest
    ): Response<ProblemResponse>

    // ─────────────────────────────────────────────
    // ADMIN CONFIG (SuperAdmin only)
    // ─────────────────────────────────────────────
    @GET("api/admin/config")
    suspend fun getAllConfig(): Response<ConfigListResponse>

    @PUT("api/admin/config")
    suspend fun updateConfig(
        @Body request: UpdateConfigRequest
    ): Response<SimpleResponse>

    @POST("api/admin/config/test-smtp")
    suspend fun testSmtpConfig(
        @Body request: TestSmtpRequest
    ): Response<SimpleResponse>

    // ═════════════════════════════════════════════
    // NOTIFICATION ROUTES  (/api/notifications/*)
    // ═════════════════════════════════════════════

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 30,
        @Query("skip") skip: Int = 0,
        @Query("unreadOnly") unreadOnly: Boolean = false
    ): Response<NotificationsResponse>

    @GET("api/notifications/unread-count")
    suspend fun getNotificationsUnreadCount(): Response<UnreadCountResponse>

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Path("id") notificationId: String
    ): Response<SimpleResponse>

    @PATCH("api/notifications/{id}/unread")
    suspend fun markNotificationUnread(
        @Path("id") notificationId: String
    ): Response<SimpleResponse>

    @PATCH("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<SimpleResponse>

    @POST("api/notifications/mark-bulk-read")
    suspend fun markBulkNotificationsRead(
        @Body request: MarkBulkReadRequest
    ): Response<SimpleResponse>

    @DELETE("api/notifications/{id}")
    suspend fun deleteNotification(
        @Path("id") notificationId: String
    ): Response<SimpleResponse>

    @DELETE("api/notifications/clear-all")
    suspend fun clearAllNotifications(): Response<SimpleResponse>

    // ═════════════════════════════════════════════
    // MESSAGE ROUTES  (/api/messages/*)
    // ═════════════════════════════════════════════

    @GET("api/messages/rooms")
    suspend fun getChatRooms(): Response<RoomsResponse>

    @GET("api/messages/{room}")
    suspend fun getRoomMessages(
        @Path("room") room: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): Response<MessagesResponse>

    @POST("api/messages/{room}")
    suspend fun sendMessage(
        @Path("room") room: String,
        @Body request: SendMessageRequest
    ): Response<SingleMessageResponse>

    @PATCH("api/messages/{room}/read")
    suspend fun markRoomRead(
        @Path("room") room: String
    ): Response<SimpleResponse>

    @DELETE("api/messages/{id}")
    suspend fun deleteMessage(
        @Path("id") messageId: String
    ): Response<SimpleResponse>

    @GET("api/messages/unread-count")
    suspend fun getMessagesUnreadCount(): Response<UnreadCountResponse>

    // ═════════════════════════════════════════════
    // CHATBOT ROUTES  (/api/chat/*)
    // ═════════════════════════════════════════════

    @POST("api/chat/message")
    suspend fun sendChatMessage(
        @Body request: ChatbotRequest
    ): Response<ChatbotResponse>

    @GET("api/chat/status")
    suspend fun getChatStatus(): Response<ChatStatusResponse>
}
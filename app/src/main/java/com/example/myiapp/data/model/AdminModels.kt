package com.example.myiapp.data.model

import com.google.gson.annotations.SerializedName

// ═════════════════════════════════════════════
// ADMIN — DASHBOARD STATS
// ═════════════════════════════════════════════

data class AdminDashboardResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("stats")    val stats: AdminStats? = null
)

data class AdminStats(
    @SerializedName("users")        val users: AdminUserStats = AdminUserStats(),
    @SerializedName("communities")  val communities: AdminCommunityStats = AdminCommunityStats(),
    @SerializedName("problems")     val problems: AdminProblemStats = AdminProblemStats(),
    @SerializedName("performance")  val performance: AdminPerformanceStats = AdminPerformanceStats()
)

data class AdminUserStats(
    @SerializedName("total")        val total: Int = 0,
    @SerializedName("residents")    val residents: Int = 0,
    @SerializedName("staff")        val staff: Int = 0,
    @SerializedName("admins")       val admins: Int = 0
)

data class AdminCommunityStats(
    @SerializedName("total")        val total: Int = 0
)

data class AdminProblemStats(
    @SerializedName("total")                val total: Int = 0,
    @SerializedName("open")                 val open: Int = 0,
    @SerializedName("inProgress")           val inProgress: Int = 0,
    @SerializedName("resolved")             val resolved: Int = 0,
    @SerializedName("rejected")             val rejected: Int = 0,
    @SerializedName("pendingFeedback")      val pendingFeedback: Int = 0,
    @SerializedName("highPriorityOpen")     val highPriorityOpen: Int = 0
)

data class AdminPerformanceStats(
    @SerializedName("avgAssignHours")       val avgAssignHours: Double = 0.0,
    @SerializedName("avgResolveHours")      val avgResolveHours: Double = 0.0,
    @SerializedName("avgRating")            val avgRating: Double = 0.0,
    @SerializedName("totalRatings")         val totalRatings: Int = 0,
    @SerializedName("verificationRate")     val verificationRate: Int = 0
)

// ═════════════════════════════════════════════
// ADMIN — USER MANAGEMENT
// ═════════════════════════════════════════════

data class AdminUserListResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("count")    val count: Int = 0,
    @SerializedName("total")    val total: Int = 0,
    @SerializedName("users")    val users: List<AdminUser> = emptyList()
)

data class AdminUser(
    @SerializedName("id")               val id: String = "",
    @SerializedName("name")             val name: String = "",
    @SerializedName("email")            val email: String = "",
    @SerializedName("phone")            val phone: String = "",
    @SerializedName("role")             val role: String = "Resident",
    @SerializedName("avatarUrl")        val avatarUrl: String? = null,
    @SerializedName("isActive")         val isActive: Boolean = true,
    @SerializedName("isVerified")       val isVerified: Boolean = false,
    @SerializedName("communityId")      val communityId: String? = null,
    @SerializedName("communityName")    val communityName: String = "",
    @SerializedName("createdAt")        val createdAt: String? = null
) {
    fun isResident(): Boolean = role == "Resident"
    fun isStaff(): Boolean = role == "Staff"
    fun isAdmin(): Boolean = role == "Admin"
    fun isSuperAdmin(): Boolean = role == "SuperAdmin"
    fun isMaintainer(): Boolean = role == "Staff" || role == "Admin"
    fun roleLabel(): String = role
    fun initial(): String = name.firstOrNull()?.uppercase() ?: "?"
}

data class AdminCreateUserRequest(
    @SerializedName("name")         val name: String,
    @SerializedName("email")        val email: String,
    @SerializedName("phone")        val phone: String,
    @SerializedName("password")     val password: String,
    @SerializedName("role")         val role: String,
    @SerializedName("communityId")  val communityId: String? = null
)

data class AdminUpdateUserRequest(
    @SerializedName("name")         val name: String? = null,
    @SerializedName("phone")        val phone: String? = null,
    @SerializedName("role")         val role: String? = null,
    @SerializedName("communityId")  val communityId: String? = null,
    @SerializedName("isActive")     val isActive: Boolean? = null,
    @SerializedName("avatarUrl")    val avatarUrl: String? = null
)

data class AdminUserResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("user")     val user: AdminUser? = null
)

data class PromoteUserRequest(
    @SerializedName("role") val role: String
)

// ═════════════════════════════════════════════
// ADMIN — MAINTAINER STATS (list)
// ═════════════════════════════════════════════

data class AdminMaintainersResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("count")        val count: Int = 0,
    @SerializedName("maintainers")  val maintainers: List<AdminMaintainer> = emptyList()
)

data class AdminMaintainer(
    @SerializedName("id")               val id: String = "",
    @SerializedName("name")             val name: String = "",
    @SerializedName("email")            val email: String = "",
    @SerializedName("phone")            val phone: String = "",
    @SerializedName("role")             val role: String = "Staff",
    @SerializedName("avatarUrl")        val avatarUrl: String? = null,
    @SerializedName("communityId")      val communityId: String? = null,
    @SerializedName("communityName")    val communityName: String = "",
    @SerializedName("isActive")         val isActive: Boolean = true,
    @SerializedName("isVerified")       val isVerified: Boolean = false,
    @SerializedName("stats")            val stats: MaintainerStatsDetail = MaintainerStatsDetail()
) {
    fun isOverloaded(): Boolean = stats.activeTasks >= 5
    fun hasGoodRating(): Boolean = stats.avgRating >= 4.0
    fun workloadLabel(): String = when {
        stats.activeTasks == 0 -> "Available"
        stats.activeTasks <= 2 -> "Light load"
        stats.activeTasks <= 4 -> "Busy"
        else -> "Overloaded"
    }
}

/**
 * Detailed stats for a maintainer in a list (admin view).
 * NOTE: Different shape from `MaintainerStats` (ProblemModels.kt)
 *       which is used for the maintainer's own dashboard.
 */
data class MaintainerStatsDetail(
    @SerializedName("activeTasks")      val activeTasks: Int = 0,
    @SerializedName("resolvedTasks")    val resolvedTasks: Int = 0,
    @SerializedName("totalTasks")       val totalTasks: Int = 0,
    @SerializedName("avgResolveHours")  val avgResolveHours: Double = 0.0,
    @SerializedName("avgRating")        val avgRating: Double = 0.0,
    @SerializedName("totalRatings")     val totalRatings: Int = 0,
    @SerializedName("completionRate")   val completionRate: Int = 0
)

// ═════════════════════════════════════════════
// ADMIN — MAINTAINER DETAIL (single)
// ═════════════════════════════════════════════

data class AdminMaintainerDetailResponse(
    @SerializedName("success")              val success: Boolean = false,
    @SerializedName("message")              val message: String? = null,
    @SerializedName("maintainer")           val maintainer: AdminUser? = null,
    @SerializedName("stats")                val stats: MaintainerDetailStats? = null,
    @SerializedName("activeProblems")       val activeProblems: List<Problem> = emptyList(),
    @SerializedName("resolvedProblems")     val resolvedProblems: List<Problem> = emptyList()
)

data class MaintainerDetailStats(
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

// ═════════════════════════════════════════════
// ADMIN — REASSIGN PROBLEM
// ═════════════════════════════════════════════

data class ReassignProblemRequest(
    @SerializedName("maintainerId") val maintainerId: String,
    @SerializedName("reason")       val reason: String? = null
)

// ═════════════════════════════════════════════
// ADMIN — CREATE PROBLEM ON BEHALF
// ═════════════════════════════════════════════

data class CreateProblemOnBehalfRequest(
    @SerializedName("reporterId")   val reporterId: String,
    @SerializedName("title")        val title: String,
    @SerializedName("category")     val category: String = "Other",
    @SerializedName("location")     val location: String,
    @SerializedName("description")  val description: String,
    @SerializedName("priority")     val priority: String = "Medium",
    @SerializedName("beforeImage")  val beforeImage: String? = null
)

// ═════════════════════════════════════════════
// ADMIN — FULL PROBLEM EDIT
// ═════════════════════════════════════════════

data class AdminEditProblemRequest(
    @SerializedName("title")            val title: String? = null,
    @SerializedName("description")      val description: String? = null,
    @SerializedName("location")         val location: String? = null,
    @SerializedName("category")         val category: String? = null,
    @SerializedName("priority")         val priority: String? = null,
    @SerializedName("status")           val status: String? = null,
    @SerializedName("assignedTo")       val assignedTo: String? = null,
    @SerializedName("beforeImage")      val beforeImage: String? = null,
    @SerializedName("afterImage")       val afterImage: String? = null,
    @SerializedName("feedbackStatus")   val feedbackStatus: String? = null
)

// ═════════════════════════════════════════════
// ADMIN — PROBLEM VERIFICATION DETAIL  (NEW)
// ═════════════════════════════════════════════
// Response for GET /api/admin/problems/:id/verification
// Includes verification, timing, notification, checklist,
// images, and YOLO sections for the admin verification screen.

data class AdminProblemVerificationResponse(
    @SerializedName("success")       val success: Boolean = false,
    @SerializedName("message")       val message: String? = null,
    @SerializedName("problem")       val problem: Problem? = null,
    @SerializedName("verification")  val verification: ResolutionVerification? = null,
    @SerializedName("timing")        val timing: AdminTimingDetail? = null,
    @SerializedName("notification")  val notification: AdminNotificationDetail? = null,
    @SerializedName("checklist")     val checklist: AdminChecklistDetail? = null,
    @SerializedName("images")        val images: AdminImageDetail? = null,
    @SerializedName("yolo")          val yolo: AdminYoloDetail? = null
)

data class AdminTimingDetail(
    @SerializedName("createdAt")            val createdAt: String? = null,
    @SerializedName("firstAssignedAt")      val firstAssignedAt: String? = null,
    @SerializedName("assignedAt")           val assignedAt: String? = null,
    @SerializedName("resolvedAt")           val resolvedAt: String? = null,
    @SerializedName("timeToAssignHours")    val timeToAssignHours: Double? = null,
    @SerializedName("timeToResolveHours")   val timeToResolveHours: Double? = null,
    @SerializedName("totalTimeHours")       val totalTimeHours: Double? = null
)

data class AdminNotificationDetail(
    @SerializedName("residentNotifiedAt")     val residentNotifiedAt: String? = null,
    @SerializedName("residentViewedAt")       val residentViewedAt: String? = null,
    @SerializedName("hasResidentViewedFix")   val hasResidentViewedFix: Boolean = false
)

data class AdminChecklistDetail(
    @SerializedName("progressPercent")        val progressPercent: Int = 0,
    @SerializedName("totalSteps")             val totalSteps: Int = 0,
    @SerializedName("completedSteps")         val completedSteps: Int = 0,
    @SerializedName("isChecklistCompleted")   val isChecklistCompleted: Boolean = false
)

data class AdminImageDetail(
    @SerializedName("beforeImage")  val beforeImage: String = "",
    @SerializedName("afterImage")   val afterImage: String = ""
)

data class AdminYoloDetail(
    @SerializedName("beforeDetections") val beforeDetections: List<YoloDetection> = emptyList(),
    @SerializedName("afterDetections")  val afterDetections: List<YoloDetection> = emptyList(),
    @SerializedName("modelName")        val modelName: String = "",
    @SerializedName("inferenceMs")      val inferenceMs: Int = 0
)

// ═════════════════════════════════════════════
// ADMIN — VERIFICATION OVERRIDE  (NEW)
// ═════════════════════════════════════════════
// Request for PATCH /api/admin/problems/:id/override-verification

data class OverrideVerificationRequest(
    @SerializedName("verdict")  val verdict: String,
    @SerializedName("note")     val note: String? = null
) {
    companion object {
        const val VERIFIED = "VERIFIED"
        const val PARTIAL = "PARTIAL"
        const val NOT_VERIFIED = "NOT_VERIFIED"
        const val UNVERIFIABLE = "UNVERIFIABLE"

        val ALL_VERDICTS = listOf(VERIFIED, PARTIAL, NOT_VERIFIED, UNVERIFIABLE)
    }
}
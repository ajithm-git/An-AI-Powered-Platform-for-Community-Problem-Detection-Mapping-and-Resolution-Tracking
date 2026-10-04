package com.example.myiapp.data.model

import com.google.gson.annotations.SerializedName

// ═════════════════════════════════════════════
// PROBLEM MODELS
// ═════════════════════════════════════════════

// ─────────────────────────────────────────────
// Request: Create a new problem (final save)
// ─────────────────────────────────────────────
data class CreateProblemRequest(
    @SerializedName("title")        val title: String,
    @SerializedName("category")     val category: String,
    @SerializedName("location")     val location: String,
    @SerializedName("description")  val description: String,
    @SerializedName("priority")     val priority: String = "Medium",
    @SerializedName("beforeImage")  val beforeImage: String? = null
)

// ─────────────────────────────────────────────
// Request: Analyze image (draft only, no save)
// ─────────────────────────────────────────────
data class AnalyzeProblemRequest(
    @SerializedName("beforeImage")  val beforeImage: String,
    @SerializedName("locationHint") val locationHint: String? = null
)

// ─────────────────────────────────────────────
// Response: Analysis result
// ─────────────────────────────────────────────
data class AnalyzeProblemResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("yolo")     val yolo: YoloAnalysisResult? = null,
    @SerializedName("draft")    val draft: ProblemDraft? = null
)

data class YoloAnalysisResult(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("modelLoaded")  val modelLoaded: String = "",
    @SerializedName("inferenceMs")  val inferenceMs: Int = 0,
    @SerializedName("detections")   val detections: List<YoloDetection> = emptyList()
)

data class ProblemDraft(
    @SerializedName("title")                val title: String = "",
    @SerializedName("description")          val description: String = "",
    @SerializedName("category")             val category: String = "Other",
    @SerializedName("priority")             val priority: String = "Medium",
    @SerializedName("severity")             val severity: String = "Medium",
    @SerializedName("estimatedHours")       val estimatedHours: Int = 2,
    @SerializedName("recommendedAction")    val recommendedAction: String = "",
    @SerializedName("confidenceScore")      val confidenceScore: Int = 80,
    @SerializedName("detectedObjects")      val detectedObjects: List<String> = emptyList()
)

// ─────────────────────────────────────────────
// Request: Update a problem
// ─────────────────────────────────────────────
data class UpdateProblemRequest(
    @SerializedName("title")        val title: String? = null,
    @SerializedName("category")     val category: String? = null,
    @SerializedName("location")     val location: String? = null,
    @SerializedName("description")  val description: String? = null,
    @SerializedName("priority")     val priority: String? = null
)

// ─────────────────────────────────────────────
// Response: Like toggle
// ─────────────────────────────────────────────
data class LikeResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("liked")        val liked: Boolean = false,
    @SerializedName("likesCount")   val likesCount: Int = 0
)

data class AddCommentRequest(
    @SerializedName("content") val content: String
)

data class AssignProblemRequest(
    @SerializedName("maintainerId") val maintainerId: String
)

data class UpdateStatusRequest(
    @SerializedName("status")       val status: String? = null,
    @SerializedName("note")         val note: String? = null,
    @SerializedName("afterImage")   val afterImage: String? = null
)

// ═════════════════════════════════════════════
// VERIFY RESOLUTION
// ═════════════════════════════════════════════

data class VerifyResolutionRequest(
    @SerializedName("afterImage")   val afterImage: String,
    @SerializedName("note")         val note: String? = null
)

data class VerifyResolutionResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("verification") val verification: ResolutionVerification? = null,
    @SerializedName("problem")      val problem: Problem? = null
)

data class ResolutionVerification(
    @SerializedName("verdict")              val verdict: String = "UNVERIFIABLE",
    @SerializedName("confidence")           val confidence: Int = 0,
    @SerializedName("summary")              val summary: String = "",
    @SerializedName("reason")               val reason: String = "",
    @SerializedName("beforeClasses")        val beforeClasses: List<String> = emptyList(),
    @SerializedName("afterClasses")         val afterClasses: List<String> = emptyList(),
    @SerializedName("stillPresent")         val stillPresent: List<String> = emptyList(),
    @SerializedName("newIssues")            val newIssues: List<String> = emptyList(),
    @SerializedName("resolved")             val resolved: List<String> = emptyList(),
    @SerializedName("verifiedAt")           val verifiedAt: String? = null,
    @SerializedName("verifiedBy")           val verifiedBy: String? = null,
    // Admin override tracking
    @SerializedName("adminOverride")        val adminOverride: Boolean = false,
    @SerializedName("adminOverrideBy")      val adminOverrideBy: String? = null,
    @SerializedName("adminOverrideAt")      val adminOverrideAt: String? = null,
    @SerializedName("adminOverrideNote")    val adminOverrideNote: String = ""
) {
    fun isVerified(): Boolean = verdict == "VERIFIED"
    fun isPartial(): Boolean = verdict == "PARTIAL"
    fun isNotVerified(): Boolean = verdict == "NOT_VERIFIED"
    fun isUnverifiable(): Boolean = verdict == "UNVERIFIABLE"
}

// ═════════════════════════════════════════════
// FEEDBACK
// ═════════════════════════════════════════════

data class SubmitFeedbackRequest(
    @SerializedName("rating")       val rating: Int,
    @SerializedName("comment")      val comment: String? = null
)

data class SubmitFeedbackResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("feedback")     val feedback: ProblemFeedback? = null,
    @SerializedName("problem")      val problem: Problem? = null
)

data class ProblemFeedback(
    @SerializedName("rating")       val rating: Int = 0,
    @SerializedName("comment")      val comment: String = "",
    @SerializedName("submittedAt")  val submittedAt: String? = null
)

// ─────────────────────────────────────────────
// YOLO detection
// ─────────────────────────────────────────────
data class YoloDetection(
    @SerializedName("class")        val className: String = "",
    @SerializedName("classId")      val classId: Int = -1,
    @SerializedName("confidence")   val confidence: Float = 0f,
    @SerializedName("bbox")         val bbox: BoundingBox? = null
) {
    fun confidencePercent(): String = "${(confidence * 100).toInt()}%"
}

data class BoundingBox(
    @SerializedName("x1")   val x1: Float = 0f,
    @SerializedName("y1")   val y1: Float = 0f,
    @SerializedName("x2")   val x2: Float = 0f,
    @SerializedName("y2")   val y2: Float = 0f
)

data class AIDiagnostic(
    @SerializedName("summary")              val summary: String = "",
    @SerializedName("recommendedAction")    val recommendedAction: String = "",
    @SerializedName("detectedObjects")      val detectedObjects: List<String> = emptyList(),
    @SerializedName("severity")             val severity: String = "Medium",
    @SerializedName("estimatedHours")       val estimatedHours: Int = 2,
    @SerializedName("confidenceScore")      val confidenceScore: Int = 90
)

data class ProblemReporter(
    @SerializedName("id")       val id: String = "",
    @SerializedName("name")     val name: String = "",
    @SerializedName("role")     val role: String = "Resident",
    @SerializedName("email")    val email: String = "",
    @SerializedName("avatar")   val avatar: String? = null
)

data class ProblemComment(
    @SerializedName("id")               val id: String = "",
    @SerializedName("authorId")         val authorId: String = "",
    @SerializedName("authorName")       val authorName: String = "",
    @SerializedName("authorRole")       val authorRole: String = "Resident",
    @SerializedName("authorAvatar")     val authorAvatar: String? = null,
    @SerializedName("content")          val content: String = "",
    @SerializedName("createdAt")        val createdAt: String? = null
)

data class StatusHistoryEntry(
    @SerializedName("status")           val status: String = "",
    @SerializedName("changedBy")        val changedBy: String? = null,
    @SerializedName("changedByName")    val changedByName: String = "",
    @SerializedName("note")             val note: String = "",
    @SerializedName("createdAt")        val createdAt: String? = null
)

// ═════════════════════════════════════════════
// WORK CHECKLIST MODELS
// ═════════════════════════════════════════════

/**
 * A single step in the AI-generated work checklist.
 */
data class WorkStep(
    @SerializedName("id")                   val id: String = "",
    @SerializedName("stepNumber")           val stepNumber: Int = 1,
    @SerializedName("title")                val title: String = "",
    @SerializedName("description")          val description: String = "",
    @SerializedName("estimatedMinutes")     val estimatedMinutes: Int = 20,
    @SerializedName("safetyNote")           val safetyNote: String = "",
    @SerializedName("materialsNeeded")      val materialsNeeded: List<String> = emptyList(),
    @SerializedName("isCompleted")          val isCompleted: Boolean = false,
    @SerializedName("completedAt")          val completedAt: String? = null
) {
    fun hasSafetyNote(): Boolean = safetyNote.isNotBlank()
    fun hasMaterials(): Boolean = materialsNeeded.isNotEmpty()
}

/**
 * Full AI-generated work checklist for a problem.
 */
data class WorkChecklist(
    @SerializedName("steps")                    val steps: List<WorkStep> = emptyList(),
    @SerializedName("totalEstimateHours")       val totalEstimateHours: Double = 0.0,
    @SerializedName("safetyOverview")           val safetyOverview: String = "",
    @SerializedName("generatedAt")              val generatedAt: String? = null,
    @SerializedName("completedStepsCount")      val completedStepsCount: Int = 0,
    @SerializedName("isChecklistCompleted")     val isChecklistCompleted: Boolean = false
) {
    fun totalSteps(): Int = steps.size
    fun progressPercent(): Int {
        if (steps.isEmpty()) return 0
        val done = steps.count { it.isCompleted }
        return ((done.toFloat() / steps.size) * 100).toInt()
    }
    fun hasSteps(): Boolean = steps.isNotEmpty()
    fun allCompleted(): Boolean = steps.isNotEmpty() && steps.all { it.isCompleted }
    fun nextIncompleteStep(): WorkStep? = steps.firstOrNull { !it.isCompleted }
}

/**
 * Response for GET /api/problems/:id/work-checklist
 * and PATCH /api/problems/:id/work-checklist/step
 */
data class WorkChecklistResponse(
    @SerializedName("success")          val success: Boolean = false,
    @SerializedName("message")          val message: String? = null,
    @SerializedName("workChecklist")    val workChecklist: WorkChecklist? = null,
    @SerializedName("fromCache")        val fromCache: Boolean = false,
    @SerializedName("problem")          val problem: Problem? = null
)

/**
 * Request body for PATCH /api/problems/:id/work-checklist/step
 */
data class ToggleChecklistStepRequest(
    @SerializedName("stepId")       val stepId: String,
    @SerializedName("isCompleted")  val isCompleted: Boolean
)

// ═════════════════════════════════════════════
// MAINTAINER MY WORK (home list)
// ═════════════════════════════════════════════

data class MyWorkResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("notFinished")  val notFinished: List<Problem> = emptyList(),
    @SerializedName("finished")     val finished: List<Problem> = emptyList(),
    @SerializedName("counts")       val counts: MyWorkCounts = MyWorkCounts()
)

data class MyWorkCounts(
    @SerializedName("notFinished")  val notFinished: Int = 0,
    @SerializedName("finished")     val finished: Int = 0
)

// ═════════════════════════════════════════════
// RESIDENT VIEW TRACKING (Q4 gate)
// ═════════════════════════════════════════════

data class MarkViewedResponse(
    @SerializedName("success")              val success: Boolean = false,
    @SerializedName("message")              val message: String? = null,
    @SerializedName("residentViewedAt")     val residentViewedAt: String? = null,
    @SerializedName("problem")              val problem: Problem? = null
)

// ─────────────────────────────────────────────
// Model: Full Problem
// ─────────────────────────────────────────────
data class Problem(
    @SerializedName("id")                   val id: String = "",
    @SerializedName("communityId")          val communityId: String? = null,
    @SerializedName("communityName")        val communityName: String = "",
    @SerializedName("title")                val title: String = "",
    @SerializedName("category")             val category: String = "Other",
    @SerializedName("location")             val location: String = "",
    @SerializedName("description")          val description: String = "",
    @SerializedName("priority")             val priority: String = "Medium",

    @SerializedName("beforeImage")          val beforeImage: String? = null,
    @SerializedName("afterImage")           val afterImage: String? = null,

    @SerializedName("reporter")             val reporter: ProblemReporter? = null,

    // YOLO (before)
    @SerializedName("yoloDetections")       val yoloDetections: List<YoloDetection> = emptyList(),
    @SerializedName("yoloModelName")        val yoloModelName: String = "",
    @SerializedName("yoloInferenceMs")      val yoloInferenceMs: Int = 0,

    // YOLO (after)
    @SerializedName("afterYoloDetections")  val afterYoloDetections: List<YoloDetection> = emptyList(),

    // Verification
    @SerializedName("resolutionVerification") val resolutionVerification: ResolutionVerification? = null,

    // Feedback
    @SerializedName("feedbackStatus")       val feedbackStatus: String = "pending",
    @SerializedName("feedback")             val feedback: ProblemFeedback? = null,

    // Gemini diagnostic
    @SerializedName("aiDiagnostic")         val aiDiagnostic: AIDiagnostic? = null,

    // AI Work Checklist
    @SerializedName("workChecklist")        val workChecklist: WorkChecklist? = null,

    // Resident notification / view tracking (Q4)
    @SerializedName("residentNotifiedAt")   val residentNotifiedAt: String? = null,
    @SerializedName("residentViewedAt")     val residentViewedAt: String? = null,

    // Workflow
    @SerializedName("status")               val status: String = "Open",
    @SerializedName("assignedTo")           val assignedTo: String? = null,
    @SerializedName("assignedToName")       val assignedToName: String = "",
    @SerializedName("assignedAt")           val assignedAt: String? = null,
    @SerializedName("firstAssignedAt")      val firstAssignedAt: String? = null,
    @SerializedName("resolvedAt")           val resolvedAt: String? = null,
    @SerializedName("autoAssigned")         val autoAssigned: Boolean = false,

    @SerializedName("statusHistory")        val statusHistory: List<StatusHistoryEntry> = emptyList(),
    @SerializedName("likesCount")           val likesCount: Int = 0,
    @SerializedName("likedBy")              val likedBy: List<String> = emptyList(),
    @SerializedName("comments")             val comments: List<ProblemComment> = emptyList(),

    @SerializedName("editedAt")             val editedAt: String? = null,
    @SerializedName("createdAt")            val createdAt: String? = null,
    @SerializedName("updatedAt")            val updatedAt: String? = null
) {
    fun isLikedBy(userId: String): Boolean = likedBy.contains(userId)
    fun topYoloDetection(): YoloDetection? = yoloDetections.maxByOrNull { it.confidence }
    fun uniqueDetectedClasses(): List<String> = yoloDetections.map { it.className }.distinct()
    fun hasYoloDetections(): Boolean = yoloDetections.isNotEmpty()

    fun isVerified(): Boolean =
        resolutionVerification?.verdict == "VERIFIED"

    fun isPartiallyVerified(): Boolean =
        resolutionVerification?.verdict == "PARTIAL"

    fun isUnverified(): Boolean =
        resolutionVerification?.verdict == "NOT_VERIFIED"

    fun needsResidentFeedback(): Boolean =
        status == "Resolved" && feedbackStatus == "pending"

    fun hasResidentFeedback(): Boolean =
        feedbackStatus == "submitted" && feedback != null

    /**
     * True when the problem is resolved but the resident hasn't opened the fix yet.
     * Per Q4 design, the resident must view the fix before rating.
     */
    fun needsResidentViewBeforeRating(): Boolean =
        status == "Resolved" &&
                feedbackStatus == "pending" &&
                residentViewedAt.isNullOrBlank()

    /**
     * True when the resident is allowed to submit feedback right now.
     */
    fun canSubmitFeedbackNow(): Boolean =
        status == "Resolved" &&
                feedbackStatus == "pending" &&
                !residentViewedAt.isNullOrBlank()

    /**
     * True when the maintainer's work checklist exists (was generated).
     */
    fun hasChecklist(): Boolean =
        workChecklist != null && workChecklist.hasSteps()

    /**
     * True when all checklist steps are completed.
     */
    fun isChecklistDone(): Boolean =
        workChecklist?.allCompleted() == true

    /**
     * Checklist progress percent (0-100). 0 if no checklist.
     */
    fun checklistProgressPercent(): Int =
        workChecklist?.progressPercent() ?: 0

    /**
     * Human-readable status label for UI.
     */
    fun statusLabel(): String = when (status) {
        "Open"          -> "Open"
        "Assigned"      -> "Assigned"
        "In Progress"   -> "In Progress"
        "Resolved"      -> "Resolved"
        "Rejected"      -> "Rejected"
        else            -> status
    }

    /**
     * Priority sort key — higher = more urgent.
     */
    fun priorityRank(): Int = when (priority) {
        "Urgent" -> 4
        "High"   -> 3
        "Medium" -> 2
        "Low"    -> 1
        else     -> 0
    }
}

// ─────────────────────────────────────────────
// Response: Single problem
// ─────────────────────────────────────────────
data class ProblemResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("problem")  val problem: Problem? = null
)

// ─────────────────────────────────────────────
// Response: List of problems
// ─────────────────────────────────────────────
data class ProblemListResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("count")        val count: Int = 0,
    @SerializedName("communityId")  val communityId: String? = null,
    @SerializedName("problems")     val problems: List<Problem> = emptyList()
)

// ─────────────────────────────────────────────
// Response: Comment created
// ─────────────────────────────────────────────
data class CommentResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("comment")  val comment: ProblemComment? = null
)

// ═════════════════════════════════════════════
// MAINTAINER DASHBOARD
// ═════════════════════════════════════════════

data class MaintainerDashboardResponse(
    @SerializedName("success")              val success: Boolean = false,
    @SerializedName("message")              val message: String? = null,
    @SerializedName("assignedToMe")         val assignedToMe: List<Problem> = emptyList(),
    @SerializedName("highPriority")         val highPriority: List<Problem> = emptyList(),
    @SerializedName("recent")               val recent: List<Problem> = emptyList(),
    @SerializedName("awaitingFeedback")     val awaitingFeedback: List<Problem> = emptyList(),
    @SerializedName("stats")                val stats: MaintainerStats = MaintainerStats()
)

data class MaintainerStats(
    @SerializedName("totalFeedback")        val totalFeedback: Int = 0,
    @SerializedName("averageRating")        val averageRating: Double = 0.0
)

// ═════════════════════════════════════════════
// NOTIFICATION MODELS
// (Also duplicated in AuthModels.kt — do not redefine here)
// ═════════════════════════════════════════════

data class AppNotification(
    @SerializedName("id")                   val id: String = "",
    @SerializedName("communityId")          val communityId: String? = null,
    @SerializedName("category")             val category: String = "SYSTEM",
    @SerializedName("priority")             val priority: String = "normal",
    @SerializedName("title")                val title: String = "",
    @SerializedName("body")                 val body: String = "",
    @SerializedName("relatedProblemId")     val relatedProblemId: String? = null,
    @SerializedName("relatedCommunityId")   val relatedCommunityId: String? = null,
    @SerializedName("relatedUserId")        val relatedUserId: String? = null,
    @SerializedName("deepLink")             val deepLink: String? = null,
    @SerializedName("actorName")            val actorName: String = "",
    @SerializedName("actorAvatar")          val actorAvatar: String? = null,
    @SerializedName("isRead")               val isRead: Boolean = false,
    @SerializedName("readAt")               val readAt: String? = null,
    @SerializedName("createdAt")            val createdAt: String? = null,
    @SerializedName("timeAgo")              val timeAgo: String = ""
)

data class NotificationsResponse(
    @SerializedName("success")          val success: Boolean = false,
    @SerializedName("message")          val message: String? = null,
    @SerializedName("unreadCount")      val unreadCount: Int = 0,
    @SerializedName("total")            val total: Int = 0,
    @SerializedName("count")            val count: Int = 0,
    @SerializedName("notifications")    val notifications: List<AppNotification> = emptyList()
)

data class UnreadCountResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("unreadCount")  val unreadCount: Int = 0
)

// ═════════════════════════════════════════════
// MESSAGE MODELS
// ═════════════════════════════════════════════

data class MessageSender(
    @SerializedName("id")       val id: String = "",
    @SerializedName("name")     val name: String = "",
    @SerializedName("role")     val role: String = "Resident",
    @SerializedName("avatar")   val avatar: String? = null
)

data class ChatMessageDto(
    @SerializedName("id")                   val id: String = "",
    @SerializedName("room")                 val room: String = "",
    @SerializedName("sender")               val sender: MessageSender? = null,
    @SerializedName("text")                 val text: String = "",
    @SerializedName("imageBase64")          val imageBase64: String? = null,
    @SerializedName("relatedProblemId")     val relatedProblemId: String? = null,
    @SerializedName("isMine")               val isMine: Boolean = false,
    @SerializedName("isRead")               val isRead: Boolean = false,
    @SerializedName("isPinned")             val isPinned: Boolean = false,
    @SerializedName("createdAt")            val createdAt: String? = null,
    @SerializedName("timeAgo")              val timeAgo: String = ""
)

data class SendMessageRequest(
    @SerializedName("text")                 val text: String? = null,
    @SerializedName("imageBase64")          val imageBase64: String? = null,
    @SerializedName("relatedProblemId")     val relatedProblemId: String? = null
)

data class MessagesResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("room")     val room: String = "",
    @SerializedName("count")    val count: Int = 0,
    @SerializedName("messages") val messages: List<ChatMessageDto> = emptyList()
)

data class SingleMessageResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("message")  val message: String? = null,
    @SerializedName("data")     val data: ChatMessageDto? = null
)

data class ChatRoom(
    @SerializedName("id")               val id: String = "",
    @SerializedName("name")             val name: String = "",
    @SerializedName("description")      val description: String = "",
    @SerializedName("messageCount")     val messageCount: Int = 0,
    @SerializedName("lastMessage")      val lastMessage: RoomLastMessage? = null
)

data class RoomLastMessage(
    @SerializedName("text")         val text: String = "",
    @SerializedName("senderName")   val senderName: String = "",
    @SerializedName("createdAt")    val createdAt: String? = null
)

data class RoomsResponse(
    @SerializedName("success")  val success: Boolean = false,
    @SerializedName("rooms")    val rooms: List<ChatRoom> = emptyList()
)

// ═════════════════════════════════════════════
// CHATBOT MODELS
// ═════════════════════════════════════════════

data class ChatbotMessage(
    @SerializedName("role")     val role: String,
    @SerializedName("text")     val text: String
)

data class ChatbotRequest(
    @SerializedName("message")  val message: String,
    @SerializedName("history")  val history: List<ChatbotMessage> = emptyList()
)

data class ChatbotResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("message")      val message: String? = null,
    @SerializedName("reply")        val reply: String? = null,
    @SerializedName("timestamp")    val timestamp: String? = null
)

data class ChatStatusResponse(
    @SerializedName("success")      val success: Boolean = false,
    @SerializedName("aiAvailable")  val aiAvailable: Boolean = false,
    @SerializedName("provider")     val provider: String = "",
    @SerializedName("model")        val model: String = ""
)
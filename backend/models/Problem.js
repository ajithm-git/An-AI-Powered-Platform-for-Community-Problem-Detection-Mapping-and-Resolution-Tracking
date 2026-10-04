const mongoose = require('mongoose');

// ─────────────────────────────────────────────
// Comment sub-schema
// ─────────────────────────────────────────────
const commentSchema = new mongoose.Schema(
    {
        authorId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            required: true
        },
        authorName: { type: String, required: true },
        authorRole: { type: String, default: 'Resident' },
        authorAvatar: { type: String, default: '' },
        content: {
            type: String,
            required: true,
            trim: true,
            maxlength: 1000
        }
    },
    { timestamps: true }
);

// ─────────────────────────────────────────────
// Status history entry
// ─────────────────────────────────────────────
const statusHistorySchema = new mongoose.Schema(
    {
        status: {
            type: String,
            enum: ['Open', 'Assigned', 'In Progress', 'Resolved', 'Rejected'],
            required: true
        },
        changedBy: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User'
        },
        changedByName: { type: String, default: '' },
        note: { type: String, default: '' }
    },
    { timestamps: true }
);

// ─────────────────────────────────────────────
// YOLO detection sub-schema
// ─────────────────────────────────────────────
const yoloDetectionSchema = new mongoose.Schema(
    {
        class: { type: String, required: true, trim: true },
        classId: { type: Number, default: -1 },
        confidence: { type: Number, min: 0, max: 1, default: 0 },
        bbox: {
            x1: { type: Number, default: 0 },
            y1: { type: Number, default: 0 },
            x2: { type: Number, default: 0 },
            y2: { type: Number, default: 0 }
        }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Resolution Verification sub-schema
// ─────────────────────────────────────────────
const resolutionVerificationSchema = new mongoose.Schema(
    {
        verdict: {
            type: String,
            enum: ['VERIFIED', 'PARTIAL', 'NOT_VERIFIED', 'UNVERIFIABLE'],
            default: 'UNVERIFIABLE'
        },
        confidence: { type: Number, min: 0, max: 100, default: 0 },
        summary: { type: String, default: '', trim: true, maxlength: 500 },
        beforeClasses: [{ type: String }],
        afterClasses: [{ type: String }],
        stillPresent: [{ type: String }],
        newIssues: [{ type: String }],
        resolved: [{ type: String }],
        verifiedAt: { type: Date, default: null },
        verifiedBy: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        adminOverride: { type: Boolean, default: false },
        adminOverrideBy: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        adminOverrideAt: { type: Date, default: null },
        adminOverrideNote: { type: String, default: '' }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Resident Feedback sub-schema
// ─────────────────────────────────────────────
const feedbackSchema = new mongoose.Schema(
    {
        rating: { type: Number, min: 1, max: 5, default: null },
        comment: { type: String, default: '', trim: true, maxlength: 500 },
        submittedAt: { type: Date, default: null }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Reassignment history entry
// ─────────────────────────────────────────────
const reassignmentSchema = new mongoose.Schema(
    {
        fromId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        fromName: { type: String, default: 'Unassigned' },
        toId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        toName: { type: String, default: '' },
        byId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        byName: { type: String, default: 'System' },
        reason: { type: String, default: '', trim: true, maxlength: 300 },
        wasAuto: { type: Boolean, default: false }
    },
    { timestamps: true }
);

// ═════════════════════════════════════════════
// Work Checklist sub-schema
// ═════════════════════════════════════════════
const workStepSchema = new mongoose.Schema(
    {
        stepNumber: { type: Number, default: 1 },
        title: { type: String, default: '', trim: true, maxlength: 100 },
        description: { type: String, default: '', trim: true, maxlength: 400 },
        estimatedMinutes: { type: Number, min: 1, max: 480, default: 20 },
        safetyNote: { type: String, default: '', trim: true, maxlength: 200 },
        materialsNeeded: [{ type: String, trim: true, maxlength: 60 }],
        // Mark as completed by the maintainer
        isCompleted: { type: Boolean, default: false },
        completedAt: { type: Date, default: null }
    },
    { _id: true }
);

const workChecklistSchema = new mongoose.Schema(
    {
        steps: { type: [workStepSchema], default: [] },
        totalEstimateHours: { type: Number, default: 0 },
        safetyOverview: { type: String, default: '', trim: true, maxlength: 300 },
        generatedAt: { type: Date, default: null },
        // Track progress
        completedStepsCount: { type: Number, default: 0 },
        // Mark the whole checklist as done when maintainer finishes
        isChecklistCompleted: { type: Boolean, default: false }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Problem schema
// ─────────────────────────────────────────────
const problemSchema = new mongoose.Schema(
    {
        // ─── Community scope ────────────────────
        communityId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Community',
            required: [true, 'Community is required'],
            index: true
        },
        communityName: { type: String, default: '', trim: true },

        // ─── Core info ──────────────────────────
        title: {
            type: String,
            required: [true, 'Title is required'],
            trim: true,
            maxlength: 120
        },
        category: {
            type: String,
            required: true,
            enum: [
                'Lighting', 'Plumbing', 'Electrical', 'Elevator',
                'Security', 'Cleanliness', 'Structural', 'HVAC',
                'Parking', 'Noise', 'Other'
            ],
            default: 'Other'
        },
        location: {
            type: String,
            required: [true, 'Location is required'],
            trim: true,
            maxlength: 200
        },
        description: {
            type: String,
            required: [true, 'Description is required'],
            trim: true,
            maxlength: 2000
        },
        priority: {
            type: String,
            enum: ['Low', 'Medium', 'High', 'Urgent'],
            default: 'Medium'
        },

        // ─── Images ─────────────────────────────
        beforeImage: { type: String, default: '' },
        afterImage: { type: String, default: '' },

        // ─── Reporter ───────────────────────────
        reporterId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            required: true,
            index: true
        },
        reporterName: { type: String, required: true },
        reporterRole: { type: String, default: 'Resident' },
        reporterEmail: { type: String, required: true },
        reporterAvatar: { type: String, default: '' },
        createdByAdmin: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        createdByAdminName: { type: String, default: '' },

        // ─── YOLO (before) ──────────────────────
        yoloDetections: { type: [yoloDetectionSchema], default: [] },
        yoloModelName: { type: String, default: '', trim: true },
        yoloInferenceMs: { type: Number, default: 0 },

        // ─── YOLO (after) ───────────────────────
        afterYoloDetections: { type: [yoloDetectionSchema], default: [] },

        // ─── Verification ───────────────────────
        resolutionVerification: {
            type: resolutionVerificationSchema,
            default: () => ({})
        },

        // ─── Feedback ───────────────────────────
        feedbackStatus: {
            type: String,
            enum: ['pending', 'submitted'],
            default: 'pending',
            index: true
        },
        feedback: {
            type: feedbackSchema,
            default: () => ({})
        },

        // ─── AI Diagnostic ──────────────────────
        aiDiagnostic: {
            summary: { type: String, default: '' },
            recommendedAction: { type: String, default: '' },
            detectedObjects: [{ type: String }],
            severity: {
                type: String,
                enum: ['Low', 'Medium', 'High', 'Critical'],
                default: 'Medium'
            },
            estimatedHours: { type: Number, default: 2 },
            confidenceScore: { type: Number, default: 90 }
        },

        // ═════════════════════════════════════════════
        // AI Work Checklist (per maintainer)
        // ═════════════════════════════════════════════
        workChecklist: {
            type: workChecklistSchema,
            default: () => ({})
        },

        // ═════════════════════════════════════════════
        // Resident notification / view tracking
        // Used for Q4: only allow rating after resident has seen the fix
        // ═════════════════════════════════════════════
        residentNotifiedAt: { type: Date, default: null },
        residentViewedAt: { type: Date, default: null },

        // ─── Workflow ───────────────────────────
        status: {
            type: String,
            enum: ['Open', 'Assigned', 'In Progress', 'Resolved', 'Rejected'],
            default: 'Open',
            index: true
        },
        assignedTo: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        assignedToName: { type: String, default: '' },
        assignedAt: { type: Date, default: null },
        firstAssignedAt: { type: Date, default: null },
        resolvedAt: { type: Date, default: null },
        autoAssigned: { type: Boolean, default: false },

        reassignHistory: { type: [reassignmentSchema], default: [] },

        statusHistory: [statusHistorySchema],

        // ─── Social ─────────────────────────────
        likes: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
        comments: [commentSchema],

        // ─── Moderation ─────────────────────────
        isDeleted: { type: Boolean, default: false },
        editedAt: { type: Date, default: null }
    },
    { timestamps: true }
);

// ═════════════════════════════════════════════
// Pre-save hook — auto-set timing + progress fields
// ═════════════════════════════════════════════
problemSchema.pre('save', function (next) {
    // First assignment
    if (this.assignedTo && !this.firstAssignedAt) {
        this.firstAssignedAt = this.assignedAt || new Date();
    }

    // Resolved timestamp
    if (this.status === 'Resolved' && !this.resolvedAt) {
        this.resolvedAt = new Date();
    }

    // If moved away from Resolved, clear resolvedAt
    if (this.status !== 'Resolved' && this.resolvedAt) {
        this.resolvedAt = null;
    }

    // Auto-compute completedStepsCount from workChecklist steps
    if (this.workChecklist && Array.isArray(this.workChecklist.steps)) {
        this.workChecklist.completedStepsCount = this.workChecklist.steps.filter(
            s => s.isCompleted === true
        ).length;
    }

    next();
});

// ─────────────────────────────────────────────
// toPublicJSON
// ─────────────────────────────────────────────
problemSchema.methods.toPublicJSON = function () {
    const obj = this.toObject();

    // Safe fallbacks to prevent crashes on legacy documents
    const likesArray = Array.isArray(obj.likes) ? obj.likes : [];
    const commentsArray = Array.isArray(obj.comments) ? obj.comments : [];

    return {
        id: obj._id,
        communityId: obj.communityId,
        communityName: obj.communityName,
        title: obj.title,
        category: obj.category,
        location: obj.location,
        description: obj.description,
        priority: obj.priority,
        beforeImage: obj.beforeImage,
        afterImage: obj.afterImage,

        reporter: {
            id: obj.reporterId,
            name: obj.reporterName,
            role: obj.reporterRole,
            email: obj.reporterEmail,
            avatar: obj.reporterAvatar
        },

        createdByAdmin: obj.createdByAdmin || null,
        createdByAdminName: obj.createdByAdminName || '',

        // ─── YOLO before ────────────────────────
        yoloDetections: (obj.yoloDetections || []).map(d => ({
            class: d.class,
            classId: d.classId,
            confidence: d.confidence,
            bbox: d.bbox
        })),
        yoloModelName: obj.yoloModelName || '',
        yoloInferenceMs: obj.yoloInferenceMs || 0,

        // ─── YOLO after ─────────────────────────
        afterYoloDetections: (obj.afterYoloDetections || []).map(d => ({
            class: d.class,
            classId: d.classId,
            confidence: d.confidence,
            bbox: d.bbox
        })),

        // ─── Verification ───────────────────────
        resolutionVerification: obj.resolutionVerification ? {
            verdict: obj.resolutionVerification.verdict || 'UNVERIFIABLE',
            confidence: obj.resolutionVerification.confidence || 0,
            summary: obj.resolutionVerification.summary || '',
            beforeClasses: obj.resolutionVerification.beforeClasses || [],
            afterClasses: obj.resolutionVerification.afterClasses || [],
            stillPresent: obj.resolutionVerification.stillPresent || [],
            newIssues: obj.resolutionVerification.newIssues || [],
            resolved: obj.resolutionVerification.resolved || [],
            verifiedAt: obj.resolutionVerification.verifiedAt,
            verifiedBy: obj.resolutionVerification.verifiedBy,
            adminOverride: obj.resolutionVerification.adminOverride || false,
            adminOverrideBy: obj.resolutionVerification.adminOverrideBy,
            adminOverrideAt: obj.resolutionVerification.adminOverrideAt,
            adminOverrideNote: obj.resolutionVerification.adminOverrideNote || ''
        } : null,

        // ─── Feedback ───────────────────────────
        feedbackStatus: obj.feedbackStatus || 'pending',
        feedback: obj.feedback ? {
            rating: obj.feedback.rating,
            comment: obj.feedback.comment || '',
            submittedAt: obj.feedback.submittedAt
        } : null,

        // ─── AI Diagnostic ──────────────────────
        aiDiagnostic: obj.aiDiagnostic,

        // ═════════════════════════════════════════
        // Work Checklist
        // ═════════════════════════════════════════
        workChecklist: obj.workChecklist ? {
            steps: (obj.workChecklist.steps || []).map((s, idx) => ({
                id: s._id ? s._id.toString() : `step_${idx}`,
                stepNumber: s.stepNumber || idx + 1,
                title: s.title || '',
                description: s.description || '',
                estimatedMinutes: s.estimatedMinutes || 20,
                safetyNote: s.safetyNote || '',
                materialsNeeded: s.materialsNeeded || [],
                isCompleted: s.isCompleted || false,
                completedAt: s.completedAt
            })),
            totalEstimateHours: obj.workChecklist.totalEstimateHours || 0,
            safetyOverview: obj.workChecklist.safetyOverview || '',
            generatedAt: obj.workChecklist.generatedAt,
            completedStepsCount: obj.workChecklist.completedStepsCount || 0,
            isChecklistCompleted: obj.workChecklist.isChecklistCompleted || false
        } : null,

        // ═════════════════════════════════════════
        // Notification / view tracking
        // ═════════════════════════════════════════
        residentNotifiedAt: obj.residentNotifiedAt || null,
        residentViewedAt: obj.residentViewedAt || null,

        // ─── Workflow ───────────────────────────
        status: obj.status,
        assignedTo: obj.assignedTo,
        assignedToName: obj.assignedToName,
        assignedAt: obj.assignedAt,
        firstAssignedAt: obj.firstAssignedAt,
        resolvedAt: obj.resolvedAt,
        autoAssigned: obj.autoAssigned || false,

        reassignHistory: (obj.reassignHistory || []).map(r => ({
            id: r._id,
            fromId: r.fromId,
            fromName: r.fromName,
            toId: r.toId,
            toName: r.toName,
            byId: r.byId,
            byName: r.byName,
            reason: r.reason,
            wasAuto: r.wasAuto,
            createdAt: r.createdAt
        })),

        statusHistory: obj.statusHistory,
        likesCount: likesArray.length,
        likedBy: likesArray,
        comments: commentsArray.map(c => ({
            id: c._id,
            authorId: c.authorId,
            authorName: c.authorName,
            authorRole: c.authorRole,
            authorAvatar: c.authorAvatar,
            content: c.content,
            createdAt: c.createdAt
        })),

        editedAt: obj.editedAt,
        createdAt: obj.createdAt,
        updatedAt: obj.updatedAt
    };
};

// ═════════════════════════════════════════════
// Helper methods — timing calculations
// ═════════════════════════════════════════════

problemSchema.methods.getTimeToAssignHours = function () {
    if (!this.firstAssignedAt) return null;
    const ms = new Date(this.firstAssignedAt).getTime() - new Date(this.createdAt).getTime();
    return Math.round((ms / (1000 * 60 * 60)) * 10) / 10;
};

problemSchema.methods.getTimeToResolveHours = function () {
    if (!this.resolvedAt || !this.firstAssignedAt) return null;
    const ms = new Date(this.resolvedAt).getTime() - new Date(this.firstAssignedAt).getTime();
    return Math.round((ms / (1000 * 60 * 60)) * 10) / 10;
};

problemSchema.methods.getTotalTimeHours = function () {
    if (!this.resolvedAt) return null;
    const ms = new Date(this.resolvedAt).getTime() - new Date(this.createdAt).getTime();
    return Math.round((ms / (1000 * 60 * 60)) * 10) / 10;
};

/**
 * Has the resident viewed the fix?
 * Required before they can submit feedback (Q4).
 */
problemSchema.methods.hasResidentViewedFix = function () {
    return Boolean(this.residentViewedAt);
};

/**
 * Calculate work checklist progress percentage (0-100).
 */
problemSchema.methods.getChecklistProgress = function () {
    if (!this.workChecklist || !Array.isArray(this.workChecklist.steps) || this.workChecklist.steps.length === 0) {
        return 0;
    }
    const total = this.workChecklist.steps.length;
    const completed = this.workChecklist.steps.filter(s => s.isCompleted).length;
    return Math.round((completed / total) * 100);
};

/**
 * Is this problem fully resolved and verified?
 * Useful for Android-side badges.
 */
problemSchema.methods.isFullyResolved = function () {
    return (
        this.status === 'Resolved' &&
        this.resolutionVerification &&
        this.resolutionVerification.verdict === 'VERIFIED'
    );
};

// ─────────────────────────────────────────────
// Virtuals
// ─────────────────────────────────────────────
problemSchema.virtual('isResolved').get(function () {
    return this.status === 'Resolved';
});

problemSchema.virtual('isAssigned').get(function () {
    return Boolean(this.assignedTo);
});

problemSchema.set('toObject', { virtuals: true });
problemSchema.set('toJSON', { virtuals: true });

// ─────────────────────────────────────────────
// Indexes
// ─────────────────────────────────────────────
problemSchema.index({ communityId: 1, createdAt: -1 });
problemSchema.index({ communityId: 1, status: 1 });
problemSchema.index({ communityId: 1, reporterId: 1 });
problemSchema.index({ communityId: 1, assignedTo: 1, status: 1 });
problemSchema.index({ feedbackStatus: 1, status: 1 });
problemSchema.index({ assignedTo: 1, status: 1 });
problemSchema.index({ status: 1, resolvedAt: -1 });
// NEW: fast lookups for notification retries
problemSchema.index({ status: 1, residentNotifiedAt: 1 });

module.exports = mongoose.model('Problem', problemSchema);
const mongoose = require('mongoose');

// ─────────────────────────────────────────────
// Notification schema
// ─────────────────────────────────────────────
const notificationSchema = new mongoose.Schema(
    {
        // ─── Community scope ────────────────────
        // For most notifications, this is set to the community
        // the recipient belongs to. For SuperAdmin notifications,
        // it can be null (global).
        communityId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Community',
            default: null,
            index: true
        },

        // ─── Recipient ──────────────────────────
        recipientId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            required: true,
            index: true
        },
        recipientEmail: {
            type: String,
            lowercase: true,
            trim: true,
            default: '',
            index: true
        },

        // ─── Category — drives icon + color ─────
        category: {
            type: String,
            enum: [
                'PROBLEM_ASSIGNED',
                'PROBLEM_UPDATED',
                'PROBLEM_RESOLVED',
                'PROBLEM_COMMENTED',
                'PROBLEM_LIKED',
                'PROBLEM_AFTER_PHOTO',
                'NEW_PROBLEM_NEARBY',
                'CHAT_MESSAGE',
                'SYSTEM',
                'ACCOUNT',
                'COMMUNITY_CREATED',
                'COMMUNITY_UPDATED',
                'COMMUNITY_DEACTIVATED',
                'WELCOME'
            ],
            required: true,
            index: true
        },

        // ─── Content ────────────────────────────
        title: {
            type: String,
            required: true,
            trim: true,
            maxlength: 120
        },
        body: {
            type: String,
            required: true,
            trim: true,
            maxlength: 500
        },

        // ─── Priority — for UI emphasis ─────────
        priority: {
            type: String,
            enum: ['low', 'normal', 'high', 'urgent'],
            default: 'normal',
            index: true
        },

        // ─── Deep-link targets (optional) ───────
        relatedProblemId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Problem',
            default: null
        },
        relatedCommunityId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Community',
            default: null
        },
        relatedUserId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },

        // ─── Actor (who triggered it) ───────────
        actorId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        actorName: { type: String, default: '' },
        actorAvatar: { type: String, default: '' },

        // ─── Read state ─────────────────────────
        isRead: {
            type: Boolean,
            default: false,
            index: true
        },
        readAt: { type: Date, default: null },

        // ─── Multi-recipient support (future group chat) ───
        readBy: [{
            userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
            readAt: { type: Date, default: Date.now }
        }],

        // ─── Delivery ───────────────────────────
        emailSent: {
            type: Boolean,
            default: false
        },

        // ─── Auto-expiry (TTL index) ────────────
        // Old notifications auto-delete after 90 days
        expiresAt: {
            type: Date,
            default: () => new Date(Date.now() + 90 * 24 * 60 * 60 * 1000), // 90 days
            index: { expires: 0 } // TTL index
        }
    },
    { timestamps: true }
);

// ─────────────────────────────────────────────
// Indexes
// ─────────────────────────────────────────────
notificationSchema.index({ recipientId: 1, createdAt: -1 });
notificationSchema.index({ recipientId: 1, isRead: 1, createdAt: -1 });
notificationSchema.index({ communityId: 1, recipientId: 1, createdAt: -1 });
notificationSchema.index({ category: 1, createdAt: -1 });

// ─────────────────────────────────────────────
// toPublicJSON
// ─────────────────────────────────────────────
notificationSchema.methods.toPublicJSON = function () {
    const obj = this.toObject();

    // Compute deep-link for Android to know what screen to open
    let deepLink = null;
    if (obj.relatedProblemId) {
        deepLink = `communityai://problem/${obj.relatedProblemId}`;
    } else if (obj.relatedCommunityId) {
        deepLink = `communityai://community/${obj.relatedCommunityId}`;
    } else if (obj.relatedUserId) {
        deepLink = `communityai://user/${obj.relatedUserId}`;
    }

    return {
        id: obj._id,
        communityId: obj.communityId,
        category: obj.category,
        priority: obj.priority || 'normal',
        title: obj.title,
        body: obj.body,
        relatedProblemId: obj.relatedProblemId,
        relatedCommunityId: obj.relatedCommunityId,
        relatedUserId: obj.relatedUserId,
        deepLink,
        actorName: obj.actorName,
        actorAvatar: obj.actorAvatar,
        isRead: obj.isRead,
        readAt: obj.readAt,
        createdAt: obj.createdAt,
        timeAgo: formatTimeAgo(obj.createdAt)
    };
};

// ─────────────────────────────────────────────
// Time ago helper
// ─────────────────────────────────────────────
function formatTimeAgo(date) {
    if (!date) return '';
    const seconds = Math.floor((Date.now() - new Date(date).getTime()) / 1000);
    if (seconds < 60) return `${seconds}s ago`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days === 1) return 'Yesterday';
    if (days < 7) return `${days}d ago`;
    return new Date(date).toLocaleDateString();
}

// ─────────────────────────────────────────────
// Static helper: mark all as read for a user
// ─────────────────────────────────────────────
notificationSchema.statics.markAllRead = async function (recipientId, communityId = null) {
    const filter = { recipientId, isRead: false };
    if (communityId) filter.communityId = communityId;

    return this.updateMany(filter, {
        $set: { isRead: true, readAt: new Date() }
    });
};

module.exports = mongoose.model('Notification', notificationSchema);
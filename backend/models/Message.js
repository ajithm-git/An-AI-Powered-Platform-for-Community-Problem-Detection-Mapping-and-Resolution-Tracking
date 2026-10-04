const mongoose = require('mongoose');

/**
 * ═══════════════════════════════════════════════════════════════════
 *  Message Model
 * ═══════════════════════════════════════════════════════════════════
 *
 *  Supports two message types in a unified collection:
 *
 *  1. ROOM messages (community group chats)
 *     room = "<communitySlug>__<templateId>"
 *     Example: "nie-north-campus__community-general"
 *     type  = "room" | "announcement" | "system"
 *
 *  2. DIRECT messages (1-on-1 chats between two users)
 *     room = "direct__<sortedUserId1>_<sortedUserId2>"
 *     Example: "direct__65a1b2c3d4e5f6a7b8c9d0e1_65a1b2c3d4e5f6a7b8c9d0e2"
 *     type  = "direct"
 *
 *  Both types share the same collection; `room` + `type` distinguish them.
 * ═══════════════════════════════════════════════════════════════════
 */
const messageSchema = new mongoose.Schema(
    {
        // ─── Community scope ────────────────────
        // Required for room messages.
        // Optional for direct messages (inherits sender's community).
        communityId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Community',
            default: null,
            index: true
        },

        // ─── Room ID ────────────────────────────
        // Format for group rooms: "<communitySlug>__<templateId>"
        // Format for direct: "direct__<userId1>_<userId2>"
        room: {
            type: String,
            required: true,
            trim: true,
            lowercase: true,
            index: true
        },

        // ─── Message type ───────────────────────
        type: {
            type: String,
            enum: ['room', 'direct', 'announcement', 'system'],
            default: 'room',
            index: true
        },

        // ─── Sender info (denormalized for fast display) ──
        senderId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            required: true,
            index: true
        },
        senderName: { type: String, required: true },
        senderRole: { type: String, default: 'Resident' },
        senderAvatar: { type: String, default: '' },

        // ─── Direct message recipient (only for type='direct') ──
        recipientId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null,
            index: true
        },

        // ─── Content ────────────────────────────
        // Text is optional when an image is provided.
        text: {
            type: String,
            default: '',
            trim: true,
            maxlength: 2000
        },

        // Optional image attachment (base64 data URI)
        imageBase64: {
            type: String,
            default: ''
        },

        // Optional problem reference (deep-linkable)
        relatedProblemId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Problem',
            default: null
        },

        // ─── Read state ─────────────────────────
        readBy: [{
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User'
        }],

        // ─── Moderation ─────────────────────────
        isDeleted: { type: Boolean, default: false, index: true },
        isPinned: { type: Boolean, default: false },
        editedAt: { type: Date, default: null }
    },
    { timestamps: true }
);

// ─────────────────────────────────────────────
// Indexes — community-scoped + direct
// ─────────────────────────────────────────────
// Fast "get messages in room X of community Y, newest first"
messageSchema.index({ communityId: 1, room: 1, createdAt: -1 });

// Fast "unread count for user in community"
messageSchema.index({ communityId: 1, readBy: 1 });

// Fast "all messages in a direct room"
messageSchema.index({ room: 1, type: 1, createdAt: -1 });

// Fast "unread direct messages for user"
messageSchema.index({ recipientId: 1, readBy: 1, isDeleted: 1 });

// Fast "recent messages by sender"
messageSchema.index({ senderId: 1, createdAt: -1 });

// ─────────────────────────────────────────────
// Pre-validate hook — ensure text or image present
// ─────────────────────────────────────────────
messageSchema.pre('validate', function (next) {
    const hasText = this.text && this.text.trim().length > 0;
    const hasImage = this.imageBase64 && this.imageBase64.length > 0;

    if (!hasText && !hasImage) {
        return next(new Error('Message must have text or an image'));
    }
    next();
});

// ─────────────────────────────────────────────
// toPublicJSON — with viewer context
// ─────────────────────────────────────────────
messageSchema.methods.toPublicJSON = function (currentUserId) {
    const obj = this.toObject();
    const uid = currentUserId ? currentUserId.toString() : null;

    return {
        id: obj._id,
        communityId: obj.communityId,
        room: obj.room,
        type: obj.type || 'room',
        sender: {
            id: obj.senderId,
            name: obj.senderName,
            role: obj.senderRole,
            avatar: obj.senderAvatar || ''
        },
        recipientId: obj.recipientId || null,
        text: obj.text || '',
        imageBase64: obj.imageBase64 || null,
        relatedProblemId: obj.relatedProblemId || null,
        isMine: uid ? obj.senderId.toString() === uid : false,
        isRead: uid ? (obj.readBy || []).some(r => r.toString() === uid) : false,
        readCount: (obj.readBy || []).length,
        isPinned: obj.isPinned || false,
        isDeleted: obj.isDeleted || false,
        editedAt: obj.editedAt || null,
        createdAt: obj.createdAt,
        updatedAt: obj.updatedAt,
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
// Static helper — build a deterministic direct room ID
// ─────────────────────────────────────────────
messageSchema.statics.buildDirectRoomId = function (userId1, userId2) {
    const sorted = [String(userId1), String(userId2)].sort();
    return `direct__${sorted[0]}_${sorted[1]}`;
};

module.exports = mongoose.model('Message', messageSchema);
const Notification = require('../models/Notification');
const mongoose = require('mongoose');

// ─────────────────────────────────────────────
// Helper: build scoped filter
// ─────────────────────────────────────────────
function scopedNotificationFilter(req, extra = {}) {
    const filter = { recipientId: req.user._id, ...extra };

    if (req.user.role === 'SuperAdmin') {
        // SuperAdmin can filter by ?communityId= (optional)
        if (req.query.communityId) {
            filter.communityId = req.query.communityId;
        }
        // else: no filter → all notifications
    } else {
        // Regular users: only their own community + global notifications (null)
        // NOTE: This is combined with other filters via $and to avoid $or collisions
        const communityCondition = {
            $or: [
                { communityId: req.user.communityId },
                { communityId: null }
            ]
        };

        // If extra already contains $or, wrap both with $and
        if (filter.$or) {
            const existingOr = filter.$or;
            delete filter.$or;
            filter.$and = [
                communityCondition,
                { $or: existingOr }
            ];
        } else {
            Object.assign(filter, communityCondition);
        }
    }
    return filter;
}

// ─────────────────────────────────────────────
// Helper: check if user can access notification
// ─────────────────────────────────────────────
function canUserAccessNotification(req, notif) {
    // Ownership check
    if (notif.recipientId.toString() !== req.user._id.toString()) {
        return { allowed: false, reason: 'Not allowed to access this notification' };
    }

    // SuperAdmin can access anything
    if (req.user.role === 'SuperAdmin') {
        return { allowed: true };
    }

    // Global notifications (communityId is null/undefined) are accessible by all
    const isGlobalNotif = notif.communityId == null;
    if (isGlobalNotif) {
        return { allowed: true };
    }

    // Otherwise must match community
    const sameCommunity = notif.communityId.toString() === req.user.communityId?.toString();
    if (!sameCommunity) {
        return { allowed: false, reason: 'Notification belongs to a different community' };
    }

    return { allowed: true };
}

// ═════════════════════════════════════════════
// @desc    Get current user's notifications
// @route   GET /api/notifications?limit=30&skip=0&unreadOnly=false&category=...
// @access  Private
// ═════════════════════════════════════════════
exports.getMyNotifications = async (req, res) => {
    try {
        const limit = Math.min(parseInt(req.query.limit) || 30, 100);
        const skip = parseInt(req.query.skip) || 0;
        const unreadOnly = req.query.unreadOnly === 'true';
        const category = req.query.category;

        const extra = {};
        if (unreadOnly) extra.isRead = false;
        if (category) extra.category = category;

        const filter = scopedNotificationFilter(req, extra);

        const [notifications, unreadCount, total] = await Promise.all([
            Notification.find(filter)
                .sort({ createdAt: -1 })
                .skip(skip)
                .limit(limit)
                .lean(),

            Notification.countDocuments(
                scopedNotificationFilter(req, { isRead: false })
            ),

            Notification.countDocuments(scopedNotificationFilter(req))
        ]);

        // Map to public JSON manually since we used .lean()
        const mappedNotifications = notifications.map(n => ({
            id: n._id,
            communityId: n.communityId,
            category: n.category,
            priority: n.priority || 'normal',
            title: n.title,
            body: n.body,
            relatedProblemId: n.relatedProblemId || null,
            relatedCommunityId: n.relatedCommunityId || null,
            relatedUserId: n.relatedUserId || null,
            deepLink: n.relatedProblemId
                ? `communityai://problem/${n.relatedProblemId}`
                : n.relatedCommunityId
                    ? `communityai://community/${n.relatedCommunityId}`
                    : n.relatedUserId
                        ? `communityai://user/${n.relatedUserId}`
                        : null,
            actorName: n.actorName || '',
            actorAvatar: n.actorAvatar || '',
            isRead: n.isRead,
            readAt: n.readAt,
            createdAt: n.createdAt,
            timeAgo: formatTimeAgo(n.createdAt)
        }));

        res.status(200).json({
            success: true,
            unreadCount,
            total,
            count: mappedNotifications.length,
            notifications: mappedNotifications
        });

    } catch (error) {
        console.error('❌ [GET NOTIFICATIONS] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Get unread count only
// @route   GET /api/notifications/unread-count
// @access  Private
// ═════════════════════════════════════════════
exports.getUnreadCount = async (req, res) => {
    try {
        const filter = scopedNotificationFilter(req, { isRead: false });
        const unreadCount = await Notification.countDocuments(filter);

        res.status(200).json({ success: true, unreadCount });

    } catch (error) {
        console.error('❌ [UNREAD COUNT] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Mark a single notification as read
// @route   PATCH /api/notifications/:id/read
// @access  Private (only own notifications)
// ═════════════════════════════════════════════
exports.markAsRead = async (req, res) => {
    try {
        const notif = await Notification.findById(req.params.id);

        if (!notif) {
            return res.status(404).json({ success: false, message: 'Notification not found' });
        }

        const access = canUserAccessNotification(req, notif);
        if (!access.allowed) {
            return res.status(403).json({ success: false, message: access.reason });
        }

        if (!notif.isRead) {
            notif.isRead = true;
            notif.readAt = new Date();
            await notif.save();
        }

        res.status(200).json({
            success: true,
            message: 'Marked as read',
            notification: notif.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [MARK READ] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Mark a single notification as unread
// @route   PATCH /api/notifications/:id/unread
// @access  Private (only own notifications)
// ═════════════════════════════════════════════
exports.markAsUnread = async (req, res) => {
    try {
        const notif = await Notification.findById(req.params.id);

        if (!notif) {
            return res.status(404).json({ success: false, message: 'Notification not found' });
        }

        const access = canUserAccessNotification(req, notif);
        if (!access.allowed) {
            return res.status(403).json({ success: false, message: access.reason });
        }

        if (notif.isRead) {
            notif.isRead = false;
            notif.readAt = null;
            await notif.save();
        }

        res.status(200).json({
            success: true,
            message: 'Marked as unread',
            notification: notif.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [MARK UNREAD] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Mark all notifications as read
// @route   PATCH /api/notifications/read-all
// @access  Private
// ═════════════════════════════════════════════
exports.markAllAsRead = async (req, res) => {
    try {
        const filter = scopedNotificationFilter(req, { isRead: false });

        const result = await Notification.updateMany(
            filter,
            { $set: { isRead: true, readAt: new Date() } }
        );

        res.status(200).json({
            success: true,
            message: 'All marked as read',
            modifiedCount: result.modifiedCount || 0
        });

    } catch (error) {
        console.error('❌ [MARK ALL READ] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Mark multiple specific notifications as read
// @route   POST /api/notifications/mark-bulk-read
// @access  Private
// Body: { ids: ["id1", "id2", ...] }
// ═════════════════════════════════════════════
exports.markBulkAsRead = async (req, res) => {
    try {
        const { ids } = req.body;

        if (!Array.isArray(ids) || ids.length === 0) {
            return res.status(400).json({ success: false, message: 'ids array is required' });
        }

        // Validate all IDs
        const validIds = ids.filter(id => mongoose.Types.ObjectId.isValid(id));
        if (validIds.length === 0) {
            return res.status(400).json({ success: false, message: 'No valid IDs provided' });
        }

        // Scope to only user's own notifications
        const baseFilter = scopedNotificationFilter(req, {
            _id: { $in: validIds },
            isRead: false
        });

        const result = await Notification.updateMany(
            baseFilter,
            { $set: { isRead: true, readAt: new Date() } }
        );

        res.status(200).json({
            success: true,
            message: `${result.modifiedCount} notification(s) marked as read`,
            modifiedCount: result.modifiedCount || 0
        });

    } catch (error) {
        console.error('❌ [MARK BULK READ] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Delete a single notification
// @route   DELETE /api/notifications/:id
// @access  Private (only own)
// ═════════════════════════════════════════════
exports.deleteNotification = async (req, res) => {
    try {
        const notif = await Notification.findById(req.params.id);
        if (!notif) {
            return res.status(404).json({ success: false, message: 'Notification not found' });
        }

        const access = canUserAccessNotification(req, notif);
        if (!access.allowed) {
            return res.status(403).json({ success: false, message: access.reason });
        }

        await Notification.findByIdAndDelete(req.params.id);

        res.status(200).json({ success: true, message: 'Notification deleted' });

    } catch (error) {
        console.error('❌ [DELETE NOTIFICATION] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Clear all notifications (scoped)
// @route   DELETE /api/notifications/clear-all
// @access  Private
// ═════════════════════════════════════════════
exports.clearAll = async (req, res) => {
    try {
        const filter = scopedNotificationFilter(req);
        const result = await Notification.deleteMany(filter);

        res.status(200).json({
            success: true,
            message: 'All notifications cleared',
            deletedCount: result.deletedCount || 0
        });

    } catch (error) {
        console.error('❌ [CLEAR ALL] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// Helper: format time ago (matches model)
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

// Export the helper for potential reuse
module.exports.scopedNotificationFilter = scopedNotificationFilter;
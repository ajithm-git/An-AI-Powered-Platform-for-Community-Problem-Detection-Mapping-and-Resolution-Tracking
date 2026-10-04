const express = require('express');
const router = express.Router();

const {
    getMyNotifications,
    getUnreadCount,
    markAsRead,
    markAsUnread,          // ← NEW
    markAllAsRead,
    markBulkAsRead,        // ← NEW
    deleteNotification,
    clearAll
} = require('../controllers/notificationController');

const { protect } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Request logger
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [NOTIFICATION ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// @route   GET /api/notifications/test
// @desc    Health check
// @access  Public
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'Notification routes are working',
        timestamp: new Date().toISOString()
    });
});

// ═════════════════════════════════════════════
// STATIC ROUTES (must come before /:id)
// ═════════════════════════════════════════════

// @route   GET /api/notifications/unread-count
// @desc    Lightweight unread count (for badge)
// @access  Private
router.get('/unread-count', protect, getUnreadCount);

// @route   PATCH /api/notifications/read-all
// @desc    Mark all notifications as read
// @access  Private
router.patch('/read-all', protect, markAllAsRead);

// @route   POST /api/notifications/mark-bulk-read
// @desc    Mark multiple notifications as read
// @access  Private
// Body: { ids: ["id1", "id2", ...] }
router.post('/mark-bulk-read', protect, markBulkAsRead);    // ← NEW

// @route   DELETE /api/notifications/clear-all
// @desc    Delete all notifications for the user
// @access  Private
router.delete('/clear-all', protect, clearAll);

// ═════════════════════════════════════════════
// FEED
// ═════════════════════════════════════════════

// @route   GET /api/notifications?limit=30&skip=0&unreadOnly=true&category=PROBLEM_RESOLVED
// @desc    Get user's notifications with pagination + filters
// @access  Private
router.get('/', protect, getMyNotifications);

// ═════════════════════════════════════════════
// DYNAMIC ROUTES (/:id)
// ═════════════════════════════════════════════

// @route   PATCH /api/notifications/:id/read
// @desc    Mark one notification as read
// @access  Private
router.patch('/:id/read', protect, markAsRead);

// @route   PATCH /api/notifications/:id/unread
// @desc    Mark one notification as unread
// @access  Private
router.patch('/:id/unread', protect, markAsUnread);         // ← NEW

// @route   DELETE /api/notifications/:id
// @desc    Delete one notification
// @access  Private
router.delete('/:id', protect, deleteNotification);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [NOTIFICATION ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
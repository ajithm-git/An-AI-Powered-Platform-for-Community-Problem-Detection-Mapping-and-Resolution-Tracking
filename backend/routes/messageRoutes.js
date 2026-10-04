const express = require('express');
const router = express.Router();

const {
    getRooms,
    getMessages,
    sendMessage,
    markRoomRead,
    deleteMessage,
    getUnreadCount,
    startDirectRoom
} = require('../controllers/messageController');

const { protect } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Request logger
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [MESSAGE ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// Health check
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'Message routes are working',
        timestamp: new Date().toISOString()
    });
});

// ═════════════════════════════════════════════
// STATIC ROUTES (must come before /:room)
// ═════════════════════════════════════════════

// List all rooms (community + direct)
router.get('/rooms', protect, getRooms);

// Unread count across all rooms
router.get('/unread-count', protect, getUnreadCount);

// Start a direct conversation with a user
router.post('/direct/start', protect, startDirectRoom);

// Delete a specific message (by its ObjectId)
// NOTE: uses /message/:id to avoid clashing with /:room
router.delete('/message/:id', protect, deleteMessage);

// ═════════════════════════════════════════════
// DYNAMIC ROUTES (/:room)
// ═════════════════════════════════════════════

// Get messages in a room
router.get('/:room', protect, getMessages);

// Send a message to a room
router.post('/:room', protect, sendMessage);

// Mark all messages in a room as read
router.patch('/:room/read', protect, markRoomRead);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [MESSAGE ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
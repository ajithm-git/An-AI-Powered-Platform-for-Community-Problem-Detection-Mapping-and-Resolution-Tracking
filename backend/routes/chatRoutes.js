const express = require('express');
const router = express.Router();

const {
    sendChatMessage,
    getChatStatus,
    getWelcome,
    testChat
} = require('../controllers/chatController');

const { protect } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Request logger
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [CHAT ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// @route   GET /api/chat/test
// @desc    Public Gemini test — open in browser
// @access  Public
// ─────────────────────────────────────────────
router.get('/test', testChat);

// ─────────────────────────────────────────────
// @route   GET /api/chat/status
// @desc    Is AI available?
// @access  Private
// ─────────────────────────────────────────────
router.get('/status', protect, getChatStatus);

// ─────────────────────────────────────────────
// @route   GET /api/chat/welcome
// @desc    Personalized welcome message (no AI call)
// @access  Private
// ─────────────────────────────────────────────
router.get('/welcome', protect, getWelcome);

// ─────────────────────────────────────────────
// @route   POST /api/chat/message
// @desc    Send a chat message to Gemini
// @access  Private
// ─────────────────────────────────────────────
router.post('/message', protect, sendChatMessage);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [CHAT ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
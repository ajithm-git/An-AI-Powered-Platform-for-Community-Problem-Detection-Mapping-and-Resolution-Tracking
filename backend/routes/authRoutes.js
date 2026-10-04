const express = require('express');
const router = express.Router();

const {
    register,
    verifyOtp,
    login,
    forgotPassword,
    resetPassword,
    ensureSuperAdmin  // ← Imported to seed SuperAdmin on startup
} = require('../controllers/authController');

// ─────────────────────────────────────────────
// Seed SuperAdmin when this route file is loaded
// (called once when server starts)
// ─────────────────────────────────────────────
ensureSuperAdmin().catch(err => {
    console.error('❌ Failed to seed SuperAdmin:', err.message);
});

// ─────────────────────────────────────────────
// Request logger for all /api/auth/* routes
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [AUTH ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// @route   GET /api/auth/test
// @desc    Health check for auth routes
// @access  Public
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'Auth routes are working',
        timestamp: new Date().toISOString()
    });
});

// ─────────────────────────────────────────────
// @route   POST /api/auth/register
// @desc    Register user & send OTP
// @access  Public
// ─────────────────────────────────────────────
router.post('/register', register);

// ─────────────────────────────────────────────
// @route   POST /api/auth/verify-otp
// @desc    Verify email with OTP
// @access  Public
// ─────────────────────────────────────────────
router.post('/verify-otp', verifyOtp);

// ─────────────────────────────────────────────
// @route   POST /api/auth/login
// @desc    Authenticate user & get token
// @access  Public
// ─────────────────────────────────────────────
router.post('/login', login);

// ─────────────────────────────────────────────
// @route   POST /api/auth/forgot-password
// @desc    Send password reset OTP
// @access  Public
// ─────────────────────────────────────────────
router.post('/forgot-password', forgotPassword);

// ─────────────────────────────────────────────
// @route   POST /api/auth/reset-password
// @desc    Reset password using OTP
// @access  Public
// ─────────────────────────────────────────────
router.post('/reset-password', resetPassword);

// ─────────────────────────────────────────────
// 404 catch-all for unmatched auth routes
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [AUTH ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
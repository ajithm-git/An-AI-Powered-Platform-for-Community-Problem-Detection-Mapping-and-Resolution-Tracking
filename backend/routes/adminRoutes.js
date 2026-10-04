const express = require('express');
const router = express.Router();

const {
    getDashboardStats,
    getAllUsers,
    createUser,
    updateUser,
    deleteUser,
    promoteUser,
    getMaintainerStats,
    getMaintainerDetail,
    reassignProblem,
    overrideVerification,
    getProblemVerificationDetail,   // ← NEW
    getCommunitySettings            // ← NEW
} = require('../controllers/adminController');

const { protect } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Request logger for all /api/admin/* routes
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [ADMIN ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// Health check
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'Admin routes are working',
        timestamp: new Date().toISOString()
    });
});

// ═════════════════════════════════════════════
// DASHBOARD STATS
// ═════════════════════════════════════════════

// @route   GET /api/admin/dashboard
// @desc    Global KPIs (users, problems, timings, ratings)
// @access  Private (Admin/SuperAdmin)
router.get('/dashboard', protect, getDashboardStats);

// ═════════════════════════════════════════════
// COMMUNITY SETTINGS
// ═════════════════════════════════════════════

// @route   GET /api/admin/community
// @desc    Get current community settings (name, stats, etc.)
// @access  Private (Admin/SuperAdmin)
router.get('/community', protect, getCommunitySettings);      // ← NEW

// ═════════════════════════════════════════════
// USER MANAGEMENT
// ═════════════════════════════════════════════

// @route   GET /api/admin/users
// @desc    List users with filters (role, search, active)
// @access  Private (Admin/SuperAdmin)
router.get('/users', protect, getAllUsers);

// @route   POST /api/admin/users
// @desc    Admin creates a user (sends OTP email)
// @access  Private (Admin/SuperAdmin)
router.post('/users', protect, createUser);

// @route   PUT /api/admin/users/:id
// @desc    Admin edits any user field
// @access  Private (Admin/SuperAdmin)
router.put('/users/:id', protect, updateUser);

// @route   DELETE /api/admin/users/:id
// @desc    Soft delete user (blocks if active tasks)
// @access  Private (Admin/SuperAdmin)
router.delete('/users/:id', protect, deleteUser);

// @route   PATCH /api/admin/users/:id/role
// @desc    Promote/demote user role
// @access  Private (Admin/SuperAdmin)
router.patch('/users/:id/role', protect, promoteUser);

// ═════════════════════════════════════════════
// MAINTAINER STATS
// ═════════════════════════════════════════════

// @route   GET /api/admin/maintainers
// @desc    List all Staff/Admin with workload + stats
// @access  Private (Admin/SuperAdmin)
router.get('/maintainers', protect, getMaintainerStats);

// @route   GET /api/admin/maintainers/:id
// @desc    Single maintainer's full profile + history
// @access  Private (Admin/SuperAdmin)
router.get('/maintainers/:id', protect, getMaintainerDetail);

// ═════════════════════════════════════════════
// PROBLEM ADMIN ACTIONS
// ═════════════════════════════════════════════

// @route   GET /api/admin/problems/:id/verification
// @desc    Get full verification detail (images, YOLO, verdict, timing, notification flags)
// @access  Private (Admin/SuperAdmin)
router.get('/problems/:id/verification', protect, getProblemVerificationDetail);  // ← NEW

// @route   PATCH /api/admin/problems/:id/reassign
// @desc    Reassign a problem to another maintainer
// @access  Private (Admin/SuperAdmin)
router.patch('/problems/:id/reassign', protect, reassignProblem);

// @route   PATCH /api/admin/problems/:id/override-verification
// @desc    Admin manually overrides verification verdict
//          Body: { verdict: "VERIFIED" | "PARTIAL" | "NOT_VERIFIED" | "UNVERIFIABLE", note? }
// @access  Private (Admin/SuperAdmin)
router.patch('/problems/:id/override-verification', protect, overrideVerification);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [ADMIN ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
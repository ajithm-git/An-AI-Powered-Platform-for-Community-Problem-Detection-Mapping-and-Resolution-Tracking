const express = require('express');
const router = express.Router();

const {
    listActiveCommunities,
    getCommunity,
    getCommunityStats,
    createCommunity,
    listAllCommunities,
    updateCommunity,
    toggleCommunity,
    deleteCommunity
} = require('../controllers/communityController');

const { protect } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Request logger
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [COMMUNITY ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// Health check
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'Community routes are working',
        timestamp: new Date().toISOString()
    });
});

// ═════════════════════════════════════════════
// PUBLIC ROUTES
// ═════════════════════════════════════════════

// @route   GET /api/communities
// @desc    List active communities (register dropdown)
// @access  Public
router.get('/', listActiveCommunities);

// ═════════════════════════════════════════════
// SUPERADMIN ROUTES (must come before /:idOrSlug)
// ═════════════════════════════════════════════

// @route   GET /api/communities/admin/all
// @desc    List all communities with member counts
// @access  Private (SuperAdmin only)
router.get('/admin/all', protect, listAllCommunities);

// @route   POST /api/communities
// @desc    Create a new community
// @access  Private (SuperAdmin only)
router.post('/', protect, createCommunity);

// @route   PUT /api/communities/:id
// @desc    Update a community
// @access  Private (SuperAdmin only)
router.put('/:id', protect, updateCommunity);

// @route   PATCH /api/communities/:id/toggle
// @desc    Toggle community active/inactive
// @access  Private (SuperAdmin only)
router.patch('/:id/toggle', protect, toggleCommunity);

// @route   GET /api/communities/:id/stats
// @desc    Community aggregated stats
// @access  Private (SuperAdmin / Admin of that community)
router.get('/:id/stats', protect, getCommunityStats);

// @route   DELETE /api/communities/:id
// @desc    Soft delete a community
// @access  Private (SuperAdmin only)
router.delete('/:id', protect, deleteCommunity);

// ═════════════════════════════════════════════
// PUBLIC SINGLE-COMMUNITY ROUTE (last — catch-all)
// ═════════════════════════════════════════════

// @route   GET /api/communities/:idOrSlug
// @desc    Get single community by ID or slug
// @access  Public
router.get('/:idOrSlug', getCommunity);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [COMMUNITY ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
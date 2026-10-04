const express = require('express');
const router = express.Router();

const {
    createProblem,
    analyzeProblemDraft,
    verifyResolution,
    submitFeedback,
    getMaintainerProblems,
    getMyWork,                  // ← NEW
    getWorkChecklist,           // ← NEW
    toggleChecklistStep,        // ← NEW
    markResidentViewed,         // ← NEW
    createProblemOnBehalf,
    adminUpdateProblem,
    getProblems,
    getProblemById,
    updateProblem,
    toggleLike,
    addComment,
    assignProblem,
    updateStatus,
    deleteProblem,
    getMyProblems,
    getAssignedProblems
} = require('../controllers/problemController');

const { protect, authorize } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Request logger
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [PROBLEM ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// Health check
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'Problem routes are working',
        timestamp: new Date().toISOString()
    });
});

// ═════════════════════════════════════════════
// AI ANALYSIS (before /:id)
// ═════════════════════════════════════════════
router.post('/analyze', protect, analyzeProblemDraft);

// ═════════════════════════════════════════════
// MAINTAINER DASHBOARD + MY WORK (before /:id)
// ═════════════════════════════════════════════

// @route   GET /api/problems/maintainer-dashboard
// @desc    Get maintainer's assigned + high-priority + recent + awaiting feedback
// @access  Private (Staff/Admin/SuperAdmin)
router.get('/maintainer-dashboard', protect, getMaintainerProblems);

// @route   GET /api/problems/my-work
// @desc    Maintainer's home — not-finished + finished, priority sorted
// @access  Private (Staff/Admin)
router.get('/my-work', protect, getMyWork);      // ← NEW

// ═════════════════════════════════════════════
// ADMIN ROUTES (before /:id)
// ═════════════════════════════════════════════

// @route   POST /api/problems/admin/create-on-behalf
// @desc    Admin creates a problem selecting a resident as reporter
// @access  Private (Admin/SuperAdmin)
router.post(
    '/admin/create-on-behalf',
    protect,
    authorize('Admin', 'SuperAdmin'),   // ← Role guard
    createProblemOnBehalf
);

// @route   PUT /api/problems/admin/:id/edit
// @desc    Admin edits ANY field of any problem
// @access  Private (Admin/SuperAdmin)
router.put(
    '/admin/:id/edit',
    protect,
    authorize('Admin', 'SuperAdmin'),   // ← Role guard
    adminUpdateProblem
);

// ═════════════════════════════════════════════
// USER-SPECIFIC ROUTES (before /:id)
// ═════════════════════════════════════════════

router.get('/mine', protect, getMyProblems);
router.get('/assigned-to-me', protect, getAssignedProblems);

// ═════════════════════════════════════════════
// FEED + CREATE
// ═════════════════════════════════════════════

router.get('/', protect, getProblems);
router.post('/', protect, createProblem);

// ═════════════════════════════════════════════
// SINGLE PROBLEM ROUTES (/:id)
// ═════════════════════════════════════════════

router.get('/:id', protect, getProblemById);
router.put('/:id', protect, updateProblem);
router.post('/:id/like', protect, toggleLike);
router.post('/:id/comment', protect, addComment);

// @route   PATCH /api/problems/:id/verify
// @desc    Verify resolution with YOLO on after-image
// @access  Private (Admin or assigned Staff)
router.patch('/:id/verify', protect, verifyResolution);

// @route   POST /api/problems/:id/feedback
// @desc    Resident submits feedback (1-5 stars + comment)
// @access  Private (reporter only)
router.post('/:id/feedback', protect, submitFeedback);

// @route   GET /api/problems/:id/work-checklist
// @desc    Get AI-generated work checklist (or generate if not exists)
// @access  Private (assigned maintainer / Admin / SuperAdmin)
router.get('/:id/work-checklist', protect, getWorkChecklist);      // ← NEW

// @route   PATCH /api/problems/:id/work-checklist/step
// @desc    Mark a checklist step as complete/incomplete
// @access  Private (assigned maintainer / Admin / SuperAdmin)
router.patch('/:id/work-checklist/step', protect, toggleChecklistStep);  // ← NEW

// @route   POST /api/problems/:id/mark-viewed
// @desc    Resident marks the fix as viewed (required before rating)
// @access  Private (reporter only)
router.post('/:id/mark-viewed', protect, markResidentViewed);      // ← NEW

router.patch('/:id/assign', protect, assignProblem);
router.patch('/:id/status', protect, updateStatus);
router.delete('/:id', protect, deleteProblem);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [PROBLEM ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
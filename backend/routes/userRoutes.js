const express = require('express');
const router = express.Router();

const {
    getProfile,
    updateProfile,
    uploadAvatar,
    changePassword,
    updateLocation,
    getLocation,
    getMaintainers
} = require('../controllers/userController');

const { protect } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Multer for avatar uploads (in-memory)
// ─────────────────────────────────────────────
let upload = null;
try {
    const multer = require('multer');
    upload = multer({
        storage: multer.memoryStorage(),
        limits: { fileSize: 2 * 1024 * 1024 }, // 2 MB
        fileFilter: (req, file, cb) => {
            if (file.mimetype && file.mimetype.startsWith('image/')) {
                cb(null, true);
            } else {
                cb(new Error('Only image files are allowed'));
            }
        }
    });
} catch (err) {
    console.warn('⚠️ multer not installed — /avatar accepts base64 JSON only');
}

// ─────────────────────────────────────────────
// Request logger
// ─────────────────────────────────────────────
router.use((req, res, next) => {
    console.log(`📥 [USER ROUTE] ${req.method} ${req.originalUrl}`);
    next();
});

// ─────────────────────────────────────────────
// Health check
// ─────────────────────────────────────────────
router.get('/test', (req, res) => {
    res.status(200).json({
        success: true,
        message: 'User routes are working',
        timestamp: new Date().toISOString()
    });
});

// ═════════════════════════════════════════════
// STATIC ROUTES (must come before dynamic ones)
// ═════════════════════════════════════════════

// Maintainers list
router.get('/maintainers', protect, getMaintainers);

// Profile
router.get('/profile', protect, getProfile);
router.put('/profile', protect, updateProfile);

// Avatar (accepts multipart `avatar` OR JSON `avatarBase64`)
if (upload) {
    router.post('/avatar', protect, upload.single('avatar'), uploadAvatar);
} else {
    router.post('/avatar', protect, uploadAvatar);
}

// Change password
router.put('/change-password', protect, changePassword);

// Location
router.post('/location', protect, updateLocation);
router.get('/location', protect, getLocation);

// ─────────────────────────────────────────────
// 404 catch-all
// ─────────────────────────────────────────────
router.use((req, res) => {
    console.log(`❓ [USER ROUTE] Unknown: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

module.exports = router;
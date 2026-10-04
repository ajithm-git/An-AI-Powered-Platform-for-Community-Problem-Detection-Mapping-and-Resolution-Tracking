const jwt = require('jsonwebtoken');
const User = require('../models/User');
const Community = require('../models/Community');

/**
 * protect
 * ─────────────────────────────────────────────
 * Express middleware that:
 *  1. Reads the JWT from the Authorization header ("Bearer <token>")
 *  2. Verifies the token with JWT_SECRET
 *  3. Loads the user from MongoDB and attaches it to req.user
 *  4. If the user has a community, loads it and attaches to req.user.community
 *  5. Rejects the request with 401/403 if anything fails
 */
const protect = async (req, res, next) => {
    let token;

    if (
        req.headers.authorization &&
        req.headers.authorization.startsWith('Bearer ')
    ) {
        try {
            token = req.headers.authorization.split(' ')[1];
            const decoded = jwt.verify(token, process.env.JWT_SECRET);

            // Load user (exclude password)
            const user = await User.findById(decoded.id).select('-password');

            if (!user) {
                return res.status(401).json({
                    success: false,
                    message: 'Not authorized — user no longer exists'
                });
            }

            if (user.isActive === false) {
                return res.status(403).json({
                    success: false,
                    message: 'Account has been deactivated. Please contact support.'
                });
            }

            // Attach user
            req.user = user;

            // If user belongs to a community, load it too
            if (user.communityId) {
                const community = await Community.findById(user.communityId);

                if (!community || community.isDeleted) {
                    console.log(`⚠️ [AUTH] User ${user.email} belongs to a deleted community`);
                    return res.status(403).json({
                        success: false,
                        message: 'Your community no longer exists. Please contact support.'
                    });
                }

                // Block access if community is deactivated (except SuperAdmin)
                if (!community.isActive && user.role !== 'SuperAdmin') {
                    return res.status(403).json({
                        success: false,
                        message: 'Your community has been temporarily deactivated.'
                    });
                }

                req.user.community = community;
            }

            next();

        } catch (error) {
            console.error('❌ [AUTH MIDDLEWARE] Token verification failed:', error.message);

            if (error.name === 'TokenExpiredError') {
                return res.status(401).json({
                    success: false,
                    message: 'Session expired. Please login again.'
                });
            }
            return res.status(401).json({
                success: false,
                message: 'Not authorized — invalid token'
            });
        }
    } else {
        console.log('❌ [AUTH MIDDLEWARE] No token provided');
        return res.status(401).json({
            success: false,
            message: 'Not authorized — no token provided'
        });
    }
};

/**
 * authorize(...roles)
 * ─────────────────────────────────────────────
 * Restrict a route to specific roles.
 * Example usage:
 *    router.put('/admin-only', protect, authorize('Admin'), handler)
 *    router.post('/', protect, authorize('SuperAdmin', 'Admin'), handler)
 */
const authorize = (...roles) => {
    return (req, res, next) => {
        if (!req.user) {
            return res.status(401).json({
                success: false,
                message: 'Not authorized — no user context'
            });
        }
        if (!roles.includes(req.user.role)) {
            console.log(`❌ [AUTH MIDDLEWARE] Access denied for role: ${req.user.role}. Required: ${roles.join(' or ')}`);
            return res.status(403).json({
                success: false,
                message: `Access denied. Required role: ${roles.join(' or ')}`
            });
        }
        next();
    };
};

/**
 * requireVerified
 * ─────────────────────────────────────────────
 * Blocks unverified users from accessing protected routes.
 * Apply this after `protect` on routes that require email verification.
 */
const requireVerified = (req, res, next) => {
    if (!req.user) {
        return res.status(401).json({
            success: false,
            message: 'Not authorized'
        });
    }
    if (!req.user.isVerified) {
        return res.status(403).json({
            success: false,
            message: 'Please verify your email address to access this resource.'
        });
    }
    next();
};

/**
 * requireCommunity
 * ─────────────────────────────────────────────
 * Ensures the user belongs to a community.
 * SuperAdmin can optionally bypass by setting `allowSuperAdmin = true`.
 *
 * Example:
 *    router.get('/feed', protect, requireCommunity(), handler)
 *    router.get('/admin-view', protect, requireCommunity(true), handler)
 */
const requireCommunity = (allowSuperAdmin = false) => {
    return (req, res, next) => {
        if (!req.user) {
            return res.status(401).json({
                success: false,
                message: 'Not authorized'
            });
        }

        // SuperAdmin bypass
        if (req.user.role === 'SuperAdmin' && allowSuperAdmin) {
            return next();
        }

        if (!req.user.communityId) {
            return res.status(403).json({
                success: false,
                message: 'You are not assigned to any community. Please contact your administrator.'
            });
        }

        next();
    };
};

/**
 * userInSameCommunity
 * ─────────────────────────────────────────────
 * Helper function (not middleware) to check if the user
 * belongs to the same community as the target resource.
 *
 * Usage in a controller:
 *    if (!userInSameCommunity(req, problem.communityId)) {
 *        return res.status(403).json({ ... });
 *    }
 */
const userInSameCommunity = (req, targetCommunityId) => {
    if (!req.user) return false;

    // SuperAdmin can access anything
    if (req.user.role === 'SuperAdmin') return true;

    if (!req.user.communityId || !targetCommunityId) return false;

    // Safe comparison using string conversion
    return req.user.communityId.toString() === targetCommunityId.toString();
};

module.exports = {
    protect,
    authorize,
    requireVerified,
    requireCommunity,
    userInSameCommunity
};
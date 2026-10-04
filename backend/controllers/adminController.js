const User = require('../models/User');
const Problem = require('../models/Problem');
const Community = require('../models/Community');
const Notification = require('../models/Notification');
const Config = require('../models/Config');
const sendEmail = require('../utils/emailService');
const { resetTransporterCache } = require('../utils/emailService');

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

function requireAdmin(req, res) {
    if (!['Admin', 'SuperAdmin'].includes(req.user.role)) {
        res.status(403).json({
            success: false,
            message: 'Access denied — Admin/SuperAdmin only'
        });
        return false;
    }
    return true;
}

function requireSuperAdmin(req, res) {
    if (req.user.role !== 'SuperAdmin') {
        res.status(403).json({
            success: false,
            message: 'Access denied — SuperAdmin only'
        });
        return false;
    }
    return true;
}

function scopedFilter(req, extra = {}) {
    const filter = { ...extra };

    if (req.user.role === 'SuperAdmin') {
        if (req.query.communityId) {
            filter.communityId = req.query.communityId;
        }
    } else {
        filter.communityId = req.user.communityId;
    }
    return filter;
}

const generateOTP = () => Math.floor(100000 + Math.random() * 900000).toString();

function buildOtpEmail(name, otp, subtitle) {
    return `
        <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 30px; background: #F8FAFC; border-radius: 16px;">
            <div style="text-align: center;">
                <h1 style="color: #10B981; margin-bottom: 8px;">Community AI</h1>
                <p style="color: #64748B; font-size: 14px;">${subtitle}</p>
            </div>
            <div style="background: #FFFFFF; padding: 24px; border-radius: 12px; margin-top: 20px;">
                <p style="color: #0F172A; font-size: 15px;">Hi ${name}, use the code below:</p>
                <h1 style="background: #ECFDF5; padding: 20px; border-radius: 10px; color: #059669; letter-spacing: 8px; text-align: center; font-size: 32px;">${otp}</h1>
                <p style="color: #94A3B8; font-size: 12px; text-align: center;">This code expires in 10 minutes. If you didn't request this, ignore this email.</p>
            </div>
        </div>
    `;
}

async function createNotification(opts) {
    try {
        await Notification.create(opts);
    } catch (err) {
        console.error('⚠️ Notification create failed:', err.message);
    }
}

// ─────────────────────────────────────────────
// Community count helpers
// ─────────────────────────────────────────────
async function incrementCommunityCount(communityId, field, by = 1) {
    if (!communityId) return;
    try {
        await Community.findByIdAndUpdate(communityId, {
            $inc: { [field]: by }
        });
    } catch (err) {
        console.error(`⚠️ Failed to update ${field} on community ${communityId}:`, err.message);
    }
}

// ═════════════════════════════════════════════
// 1. DASHBOARD STATS (optimized with aggregation)
// ═════════════════════════════════════════════
exports.getDashboardStats = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        console.log(`\n📊 [ADMIN DASHBOARD] By: ${req.user.email} (${req.user.role})`);

        const communityFilter = req.user.role === 'SuperAdmin'
            ? (req.query.communityId ? { communityId: req.query.communityId } : {})
            : { communityId: req.user.communityId };

        const [
            totalUsers,
            totalResidents,
            totalStaff,
            totalAdmins,
            totalCommunities,
            totalProblems,
            openProblems,
            inProgressProblems,
            resolvedProblems,
            rejectedProblems,
            pendingFeedback,
            verifiedProblems,
            highPriorityOpen
        ] = await Promise.all([
            User.countDocuments({ ...communityFilter, isActive: true }),
            User.countDocuments({ ...communityFilter, role: 'Resident', isActive: true }),
            User.countDocuments({ ...communityFilter, role: 'Staff', isActive: true }),
            User.countDocuments({ ...communityFilter, role: 'Admin', isActive: true }),
            req.user.role === 'SuperAdmin'
                ? Community.countDocuments({ isDeleted: false })
                : Promise.resolve(1),
            Problem.countDocuments({ ...communityFilter, isDeleted: false }),
            Problem.countDocuments({ ...communityFilter, isDeleted: false, status: 'Open' }),
            Problem.countDocuments({ ...communityFilter, isDeleted: false, status: { $in: ['Assigned', 'In Progress'] } }),
            Problem.countDocuments({ ...communityFilter, isDeleted: false, status: 'Resolved' }),
            Problem.countDocuments({ ...communityFilter, isDeleted: false, status: 'Rejected' }),
            Problem.countDocuments({ ...communityFilter, isDeleted: false, status: 'Resolved', feedbackStatus: 'pending' }),
            Problem.countDocuments({
                ...communityFilter,
                isDeleted: false,
                'resolutionVerification.verdict': 'VERIFIED'
            }),
            Problem.countDocuments({
                ...communityFilter,
                isDeleted: false,
                status: { $in: ['Open', 'Assigned', 'In Progress'] },
                priority: { $in: ['High', 'Urgent'] }
            })
        ]);

        // ─────────────────────────────────────────
        // Aggregation-based timing + rating (100x faster)
        // ─────────────────────────────────────────
        const performanceAgg = await Problem.aggregate([
            {
                $match: {
                    ...communityFilter,
                    isDeleted: false
                }
            },
            {
                $facet: {
                    timing: [
                        {
                            $match: {
                                status: 'Resolved',
                                resolvedAt: { $ne: null },
                                firstAssignedAt: { $ne: null }
                            }
                        },
                        {
                            $project: {
                                resolveHours: {
                                    $divide: [
                                        { $subtract: ['$resolvedAt', '$firstAssignedAt'] },
                                        3600000
                                    ]
                                },
                                assignHours: {
                                    $divide: [
                                        { $subtract: ['$firstAssignedAt', '$createdAt'] },
                                        3600000
                                    ]
                                }
                            }
                        },
                        {
                            $group: {
                                _id: null,
                                avgResolveHours: { $avg: '$resolveHours' },
                                avgAssignHours: { $avg: '$assignHours' },
                                count: { $sum: 1 }
                            }
                        }
                    ],
                    ratings: [
                        {
                            $match: {
                                feedbackStatus: 'submitted',
                                'feedback.rating': { $ne: null }
                            }
                        },
                        {
                            $group: {
                                _id: null,
                                avgRating: { $avg: '$feedback.rating' },
                                totalRatings: { $sum: 1 }
                            }
                        }
                    ]
                }
            }
        ]);

        const timing = performanceAgg[0]?.timing?.[0] || {};
        const ratings = performanceAgg[0]?.ratings?.[0] || {};

        const avgResolveHours = Math.round((timing.avgResolveHours || 0) * 10) / 10;
        const avgAssignHours = Math.round((timing.avgAssignHours || 0) * 10) / 10;
        const avgRating = Math.round((ratings.avgRating || 0) * 10) / 10;
        const totalRatings = ratings.totalRatings || 0;

        const verificationRate = resolvedProblems > 0
            ? Math.round((verifiedProblems / resolvedProblems) * 100)
            : 0;

        res.status(200).json({
            success: true,
            stats: {
                users: {
                    total: totalUsers,
                    residents: totalResidents,
                    staff: totalStaff,
                    admins: totalAdmins
                },
                communities: {
                    total: totalCommunities
                },
                problems: {
                    total: totalProblems,
                    open: openProblems,
                    inProgress: inProgressProblems,
                    resolved: resolvedProblems,
                    rejected: rejectedProblems,
                    pendingFeedback,
                    highPriorityOpen
                },
                performance: {
                    avgAssignHours,
                    avgResolveHours,
                    avgRating,
                    totalRatings,
                    verificationRate
                }
            }
        });

    } catch (error) {
        console.error('❌ [ADMIN DASHBOARD] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 2. LIST USERS
// ═════════════════════════════════════════════
exports.getAllUsers = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const { role, search, active } = req.query;
        const limit = Math.min(parseInt(req.query.limit) || 50, 200);
        const skip = parseInt(req.query.skip) || 0;

        const filter = scopedFilter(req);

        if (role) filter.role = role;
        if (active === 'true') filter.isActive = true;
        if (active === 'false') filter.isActive = false;

        if (search && search.trim()) {
            const regex = new RegExp(search.trim(), 'i');
            filter.$or = [
                { name: regex },
                { email: regex },
                { phone: regex }
            ];
        }

        const [users, total] = await Promise.all([
            User.find(filter)
                .select('-password -otp -resetPasswordOtp')
                .sort({ createdAt: -1 })
                .skip(skip)
                .limit(limit),
            User.countDocuments(filter)
        ]);

        res.status(200).json({
            success: true,
            count: users.length,
            total,
            users: users.map(u => ({
                id: u._id,
                name: u.name,
                email: u.email,
                phone: u.phone,
                role: u.role,
                avatarUrl: u.avatarUrl || '',
                isActive: u.isActive,
                isVerified: u.isVerified,
                communityId: u.communityId || null,
                communityName: u.communityName || '',
                createdAt: u.createdAt
            }))
        });

    } catch (error) {
        console.error('❌ [ADMIN LIST USERS] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 3. CREATE USER (fixed: no double hashing)
// ═════════════════════════════════════════════
exports.createUser = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const { name, email, phone, password, role, communityId } = req.body;

        console.log(`\n👤 [ADMIN CREATE USER] ${email} as ${role} by ${req.user.email}`);

        if (!name || !email || !phone || !password || !role) {
            return res.status(400).json({
                success: false,
                message: 'name, email, phone, password, and role are required'
            });
        }

        if (!['Resident', 'Staff', 'Admin'].includes(role)) {
            return res.status(400).json({ success: false, message: 'Invalid role' });
        }

        if (password.length < 6) {
            return res.status(400).json({ success: false, message: 'Password must be at least 6 characters' });
        }

        let targetCommunityId = communityId;
        if (req.user.role === 'Admin') {
            targetCommunityId = req.user.communityId;
        }

        if (!targetCommunityId) {
            return res.status(400).json({ success: false, message: 'Community is required' });
        }

        const community = await Community.findById(targetCommunityId);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        const cleanEmail = email.trim().toLowerCase();
        const existing = await User.findOne({ email: cleanEmail });
        if (existing) {
            return res.status(400).json({
                success: false,
                message: 'A user with this email already exists'
            });
        }

        const otp = generateOTP();
        const otpExpiry = Date.now() + 10 * 60 * 1000;

        // Password hashing handled by User pre-save hook — DO NOT hash here
        const user = new User({
            name: name.trim(),
            email: cleanEmail,
            phone: phone.trim(),
            password: password, // plain — model will hash
            role,
            communityId: community._id,
            communityName: community.name,
            otp,
            otpExpiry
        });

        await user.save();

        // Update community memberCount
        await incrementCommunityCount(community._id, 'memberCount', 1);

        try {
            await sendEmail({
                email: user.email,
                subject: 'Community AI - Verify Your Account',
                message: `Your verification code: ${otp}`,
                html: buildOtpEmail(name, otp, 'Account Created by Admin')
            });
            console.log(`✅ OTP sent to ${cleanEmail}`);
        } catch (emailError) {
            console.error('⚠️ OTP email failed:', emailError.message);
        }

        if (role === 'Staff' || role === 'Admin') {
            await createNotification({
                communityId: community._id,
                recipientId: user._id,
                recipientEmail: user.email,
                category: 'WELCOME',
                title: `Welcome to ${community.name}!`,
                body: `Your ${role} account has been created by an administrator. Verify your email to get started.`,
                relatedCommunityId: community._id,
                actorId: req.user._id,
                actorName: req.user.name
            });
        }

        res.status(201).json({
            success: true,
            message: `User created. Verification code sent to ${cleanEmail}`,
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone,
                role: user.role,
                communityId: user.communityId,
                communityName: user.communityName,
                isVerified: false,
                isActive: true,
                createdAt: user.createdAt
            }
        });

    } catch (error) {
        console.error('❌ [ADMIN CREATE USER] Error:', error);
        if (error.code === 11000) {
            return res.status(400).json({ success: false, message: 'Email already in use' });
        }
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 4. UPDATE USER
// ═════════════════════════════════════════════
exports.updateUser = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const { name, phone, role, communityId, isActive, avatarUrl } = req.body;

        const user = await User.findById(req.params.id);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }

        if (req.user.role === 'Admin') {
            if (user.communityId?.toString() !== req.user.communityId?.toString()) {
                return res.status(403).json({
                    success: false,
                    message: 'You can only edit users in your own community'
                });
            }
        }

        // Validate role if provided
        if (role !== undefined) {
            const validRoles = ['Resident', 'Staff', 'Admin'];
            if (!validRoles.includes(role)) {
                return res.status(400).json({ success: false, message: 'Invalid role' });
            }
            if (req.user.role === 'Admin' && role === 'Admin') {
                return res.status(403).json({
                    success: false,
                    message: 'Only SuperAdmin can promote to Admin'
                });
            }
        }

        console.log(`\n✏️ [ADMIN UPDATE USER] ${user.email} by ${req.user.email}`);

        if (name !== undefined) user.name = name.trim();
        if (phone !== undefined) user.phone = phone.trim();
        if (role !== undefined) user.role = role;
        if (typeof isActive === 'boolean') user.isActive = isActive;
        if (avatarUrl !== undefined) user.avatarUrl = avatarUrl;

        if (communityId !== undefined && req.user.role === 'SuperAdmin') {
            const newCommunity = await Community.findById(communityId);
            if (!newCommunity || newCommunity.isDeleted) {
                return res.status(404).json({ success: false, message: 'Community not found' });
            }

            // If community changed, update denormalized counts
            const oldCommunityId = user.communityId;
            if (oldCommunityId && oldCommunityId.toString() !== newCommunity._id.toString()) {
                await incrementCommunityCount(oldCommunityId, 'memberCount', -1);
                await incrementCommunityCount(newCommunity._id, 'memberCount', 1);
            }

            user.communityId = newCommunity._id;
            user.communityName = newCommunity.name;
        }

        await user.save();

        if (role !== undefined) {
            await createNotification({
                communityId: user.communityId,
                recipientId: user._id,
                recipientEmail: user.email,
                category: 'ACCOUNT',
                title: 'Your role has been updated',
                body: `Your role is now: ${user.role}`,
                actorId: req.user._id,
                actorName: req.user.name
            });
        }

        res.status(200).json({
            success: true,
            message: 'User updated',
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone,
                role: user.role,
                communityId: user.communityId,
                communityName: user.communityName,
                isActive: user.isActive,
                isVerified: user.isVerified,
                avatarUrl: user.avatarUrl || ''
            }
        });

    } catch (error) {
        console.error('❌ [ADMIN UPDATE USER] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 5. SOFT DELETE USER
// ═════════════════════════════════════════════
exports.deleteUser = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const user = await User.findById(req.params.id);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }

        if (user._id.toString() === req.user._id.toString()) {
            return res.status(400).json({ success: false, message: 'Cannot delete your own account' });
        }

        if (user.role === 'SuperAdmin' && req.user.role !== 'SuperAdmin') {
            return res.status(403).json({ success: false, message: 'Only SuperAdmin can delete SuperAdmin' });
        }

        if (req.user.role === 'Admin' && user.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({
                success: false,
                message: 'You can only delete users in your own community'
            });
        }

        if (['Staff', 'Admin'].includes(user.role)) {
            const activeTasks = await Problem.countDocuments({
                assignedTo: user._id,
                status: { $in: ['Assigned', 'In Progress'] },
                isDeleted: false
            });
            if (activeTasks > 0) {
                return res.status(400).json({
                    success: false,
                    message: `Cannot delete — this maintainer has ${activeTasks} active task(s). Reassign them first.`
                });
            }
        }

        user.isActive = false;
        await user.save();

        // Update community count
        await incrementCommunityCount(user.communityId, 'memberCount', -1);

        console.log(`🗑️ [ADMIN DELETE USER] Soft-deleted: ${user.email}`);

        res.status(200).json({
            success: true,
            message: 'User deactivated (soft deleted)'
        });

    } catch (error) {
        console.error('❌ [ADMIN DELETE USER] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 6. PROMOTE USER (change role)
// ═════════════════════════════════════════════
exports.promoteUser = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const { role } = req.body;
        if (!['Resident', 'Staff', 'Admin'].includes(role)) {
            return res.status(400).json({ success: false, message: 'Invalid role' });
        }

        const user = await User.findById(req.params.id);
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }

        if (req.user.role === 'Admin' && role === 'Admin') {
            return res.status(403).json({
                success: false,
                message: 'Only SuperAdmin can promote to Admin'
            });
        }

        if (req.user.role === 'Admin' && user.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const oldRole = user.role;
        user.role = role;
        await user.save();

        console.log(`⬆️ [ADMIN PROMOTE] ${user.email}: ${oldRole} → ${role}`);

        await createNotification({
            communityId: user.communityId,
            recipientId: user._id,
            recipientEmail: user.email,
            category: 'ACCOUNT',
            title: `Your role has been updated`,
            body: `You are now: ${role}`,
            actorId: req.user._id,
            actorName: req.user.name
        });

        res.status(200).json({
            success: true,
            message: `Role updated: ${oldRole} → ${role}`,
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                role: user.role
            }
        });

    } catch (error) {
        console.error('❌ [ADMIN PROMOTE] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 7. GET MAINTAINER STATS
// ═════════════════════════════════════════════
exports.getMaintainerStats = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        console.log(`\n👥 [ADMIN MAINTAINER STATS] By: ${req.user.email}`);

        const communityFilter = scopedFilter(req, {
            role: { $in: ['Staff', 'Admin'] },
            isActive: true
        });

        const maintainers = await User.find(communityFilter)
            .select('-password -otp -resetPasswordOtp')
            .sort({ name: 1 });

        const enriched = await Promise.all(
            maintainers.map(async (m) => {
                // Use a single aggregation for all stats
                const agg = await Problem.aggregate([
                    { $match: { assignedTo: m._id, isDeleted: false } },
                    {
                        $facet: {
                            counts: [
                                {
                                    $group: {
                                        _id: null,
                                        total: { $sum: 1 },
                                        active: {
                                            $sum: {
                                                $cond: [
                                                    { $in: ['$status', ['Assigned', 'In Progress']] },
                                                    1, 0
                                                ]
                                            }
                                        },
                                        resolved: {
                                            $sum: { $cond: [{ $eq: ['$status', 'Resolved'] }, 1, 0] }
                                        }
                                    }
                                }
                            ],
                            ratings: [
                                {
                                    $match: {
                                        feedbackStatus: 'submitted',
                                        'feedback.rating': { $ne: null }
                                    }
                                },
                                {
                                    $group: {
                                        _id: null,
                                        avgRating: { $avg: '$feedback.rating' },
                                        total: { $sum: 1 }
                                    }
                                }
                            ],
                            timing: [
                                {
                                    $match: {
                                        status: 'Resolved',
                                        resolvedAt: { $ne: null },
                                        firstAssignedAt: { $ne: null }
                                    }
                                },
                                {
                                    $group: {
                                        _id: null,
                                        avgResolveHours: {
                                            $avg: {
                                                $divide: [
                                                    { $subtract: ['$resolvedAt', '$firstAssignedAt'] },
                                                    3600000
                                                ]
                                            }
                                        }
                                    }
                                }
                            ]
                        }
                    }
                ]);

                const counts = agg[0]?.counts?.[0] || { total: 0, active: 0, resolved: 0 };
                const ratings = agg[0]?.ratings?.[0] || { avgRating: 0, total: 0 };
                const timing = agg[0]?.timing?.[0] || { avgResolveHours: 0 };

                return {
                    id: m._id,
                    name: m.name,
                    email: m.email,
                    phone: m.phone,
                    role: m.role,
                    avatarUrl: m.avatarUrl || '',
                    communityId: m.communityId || null,
                    communityName: m.communityName || '',
                    isActive: m.isActive,
                    isVerified: m.isVerified,
                    stats: {
                        activeTasks: counts.active,
                        resolvedTasks: counts.resolved,
                        totalTasks: counts.total,
                        avgResolveHours: Math.round((timing.avgResolveHours || 0) * 10) / 10,
                        avgRating: Math.round((ratings.avgRating || 0) * 10) / 10,
                        totalRatings: ratings.total,
                        completionRate: counts.total > 0
                            ? Math.round((counts.resolved / counts.total) * 100)
                            : 0
                    }
                };
            })
        );

        enriched.sort((a, b) => a.stats.activeTasks - b.stats.activeTasks);

        res.status(200).json({
            success: true,
            count: enriched.length,
            maintainers: enriched
        });

    } catch (error) {
        console.error('❌ [ADMIN MAINTAINER STATS] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 8. GET MAINTAINER DETAIL
// ═════════════════════════════════════════════
exports.getMaintainerDetail = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const maintainer = await User.findById(req.params.id).select('-password -otp -resetPasswordOtp');
        if (!maintainer) {
            return res.status(404).json({ success: false, message: 'Maintainer not found' });
        }

        if (!['Staff', 'Admin'].includes(maintainer.role)) {
            return res.status(400).json({ success: false, message: 'User is not a maintainer' });
        }

        if (req.user.role === 'Admin' && maintainer.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const [active, resolved, all] = await Promise.all([
            Problem.find({
                assignedTo: maintainer._id,
                status: { $in: ['Assigned', 'In Progress'] },
                isDeleted: false
            }).sort({ assignedAt: -1 }).limit(30),
            Problem.find({
                assignedTo: maintainer._id,
                status: 'Resolved',
                isDeleted: false
            }).sort({ resolvedAt: -1 }).limit(30),
            Problem.find({
                assignedTo: maintainer._id,
                isDeleted: false
            }).sort({ createdAt: -1 }).limit(100)
        ]);

        const totalTasks = all.length;
        const resolvedCount = resolved.length;
        const activeCount = active.length;

        const resolvedWithTime = all.filter(p => p.resolvedAt && p.firstAssignedAt);
        const avgResolveHours = resolvedWithTime.length > 0
            ? Math.round(
                resolvedWithTime.reduce((acc, p) =>
                    acc + (new Date(p.resolvedAt) - new Date(p.firstAssignedAt)) / 3600000, 0
                ) / resolvedWithTime.length * 10
            ) / 10
            : 0;

        const assignedWithTime = all.filter(p => p.firstAssignedAt);
        const avgAssignHours = assignedWithTime.length > 0
            ? Math.round(
                assignedWithTime.reduce((acc, p) =>
                    acc + (new Date(p.firstAssignedAt) - new Date(p.createdAt)) / 3600000, 0
                ) / assignedWithTime.length * 10
            ) / 10
            : 0;

        const ratedProblems = all.filter(p => p.feedback?.rating != null);
        const avgRating = ratedProblems.length > 0
            ? Math.round(
                ratedProblems.reduce((acc, p) => acc + p.feedback.rating, 0) / ratedProblems.length * 10
            ) / 10
            : 0;

        const verifiedCount = all.filter(p =>
            p.resolutionVerification?.verdict === 'VERIFIED'
        ).length;

        res.status(200).json({
            success: true,
            maintainer: {
                id: maintainer._id,
                name: maintainer.name,
                email: maintainer.email,
                phone: maintainer.phone,
                role: maintainer.role,
                avatarUrl: maintainer.avatarUrl || '',
                communityId: maintainer.communityId || null,
                communityName: maintainer.communityName || '',
                isActive: maintainer.isActive,
                isVerified: maintainer.isVerified,
                createdAt: maintainer.createdAt
            },
            stats: {
                activeTasks: activeCount,
                resolvedTasks: resolvedCount,
                totalTasks,
                completionRate: totalTasks > 0 ? Math.round((resolvedCount / totalTasks) * 100) : 0,
                avgAssignHours,
                avgResolveHours,
                avgRating,
                totalRatings: ratedProblems.length,
                verifiedCount
            },
            activeProblems: active.map(p => p.toPublicJSON()),
            resolvedProblems: resolved.map(p => p.toPublicJSON())
        });

    } catch (error) {
        console.error('❌ [ADMIN MAINTAINER DETAIL] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 9. REASSIGN PROBLEM
// ═════════════════════════════════════════════
exports.reassignProblem = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const { maintainerId, reason } = req.body;
        if (!maintainerId) {
            return res.status(400).json({ success: false, message: 'maintainerId is required' });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }

        if (req.user.role === 'Admin' && problem.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const newMaintainer = await User.findById(maintainerId);
        if (!newMaintainer || !['Staff', 'Admin'].includes(newMaintainer.role)) {
            return res.status(404).json({ success: false, message: 'Maintainer not found or invalid role' });
        }

        if (req.user.role === 'Admin' &&
            newMaintainer.communityId?.toString() !== problem.communityId?.toString()) {
            return res.status(400).json({
                success: false,
                message: 'Cannot reassign to a maintainer from a different community'
            });
        }

        const prevAssigneeId = problem.assignedTo;
        const prevAssigneeName = problem.assignedToName || 'Unassigned';

        console.log(`🔄 [ADMIN REASSIGN] ${problem.title}`);
        console.log(`   From: ${prevAssigneeName}`);
        console.log(`   To:   ${newMaintainer.name}`);

        problem.reassignHistory.push({
            fromId: prevAssigneeId || null,
            fromName: prevAssigneeName,
            toId: newMaintainer._id,
            toName: newMaintainer.name,
            byId: req.user._id,
            byName: req.user.name,
            reason: reason || 'Admin reassignment',
            wasAuto: false
        });

        problem.assignedTo = newMaintainer._id;
        problem.assignedToName = newMaintainer.name;
        problem.assignedAt = new Date();
        if (!problem.firstAssignedAt) {
            problem.firstAssignedAt = new Date();
        }
        if (problem.status === 'Open') {
            problem.status = 'Assigned';
        }

        problem.statusHistory.push({
            status: problem.status,
            changedBy: req.user._id,
            changedByName: req.user.name,
            note: `Reassigned from ${prevAssigneeName} to ${newMaintainer.name}${reason ? ` — ${reason}` : ''}`
        });

        await problem.save();

        await createNotification({
            communityId: problem.communityId,
            recipientId: newMaintainer._id,
            recipientEmail: newMaintainer.email,
            category: 'PROBLEM_ASSIGNED',
            title: 'New task assigned to you',
            body: `You've been assigned: "${problem.title}"${reason ? ` (${reason})` : ''}`,
            relatedProblemId: problem._id,
            actorId: req.user._id,
            actorName: req.user.name,
            actorAvatar: req.user.avatarUrl || ''
        });

        if (prevAssigneeId && prevAssigneeId.toString() !== newMaintainer._id.toString()) {
            const prevUser = await User.findById(prevAssigneeId);
            if (prevUser) {
                await createNotification({
                    communityId: problem.communityId,
                    recipientId: prevUser._id,
                    recipientEmail: prevUser.email,
                    category: 'PROBLEM_UPDATED',
                    title: 'Task reassigned away from you',
                    body: `"${problem.title}" has been reassigned to ${newMaintainer.name}${reason ? ` (${reason})` : ''}`,
                    relatedProblemId: problem._id,
                    actorId: req.user._id,
                    actorName: req.user.name,
                    actorAvatar: req.user.avatarUrl || ''
                });
            }
        }

        res.status(200).json({
            success: true,
            message: `Reassigned to ${newMaintainer.name}`,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [ADMIN REASSIGN] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 10. GET PROBLEM VERIFICATION DETAIL
// ═════════════════════════════════════════════
exports.getProblemVerificationDetail = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const problem = await Problem.findById(req.params.id)
            .populate('assignedTo', 'name email role avatarUrl')
            .populate('reporterId', 'name email role avatarUrl')
            .populate('resolutionVerification.verifiedBy', 'name email role')
            .populate('resolutionVerification.adminOverrideBy', 'name email role');

        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }

        if (
            req.user.role !== 'SuperAdmin' &&
            problem.communityId?.toString() !== req.user.communityId?.toString()
        ) {
            return res.status(403).json({
                success: false,
                message: 'This problem belongs to a different community'
            });
        }

        const v = problem.resolutionVerification || {};
        const publicProblem = problem.toPublicJSON();

        const checklistProgress = typeof problem.getChecklistProgress === 'function'
            ? problem.getChecklistProgress()
            : 0;

        const residentHasViewedFix = typeof problem.hasResidentViewedFix === 'function'
            ? problem.hasResidentViewedFix()
            : Boolean(problem.residentViewedAt);

        const timing = {
            createdAt: problem.createdAt,
            firstAssignedAt: problem.firstAssignedAt,
            assignedAt: problem.assignedAt,
            resolvedAt: problem.resolvedAt,
            timeToAssignHours: typeof problem.getTimeToAssignHours === 'function'
                ? problem.getTimeToAssignHours()
                : null,
            timeToResolveHours: typeof problem.getTimeToResolveHours === 'function'
                ? problem.getTimeToResolveHours()
                : null,
            totalTimeHours: typeof problem.getTotalTimeHours === 'function'
                ? problem.getTotalTimeHours()
                : null
        };

        const notification = {
            residentNotifiedAt: problem.residentNotifiedAt || null,
            residentViewedAt: problem.residentViewedAt || null,
            hasResidentViewedFix: residentHasViewedFix
        };

        const verification = {
            verdict: v.verdict || 'UNVERIFIABLE',
            confidence: v.confidence || 0,
            summary: v.summary || '',
            beforeClasses: v.beforeClasses || [],
            afterClasses: v.afterClasses || [],
            stillPresent: v.stillPresent || [],
            newIssues: v.newIssues || [],
            resolved: v.resolved || [],
            verifiedAt: v.verifiedAt || null,
            verifiedBy: v.verifiedBy || null,
            adminOverride: v.adminOverride || false,
            adminOverrideBy: v.adminOverrideBy || null,
            adminOverrideAt: v.adminOverrideAt || null,
            adminOverrideNote: v.adminOverrideNote || ''
        };

        res.status(200).json({
            success: true,
            problem: publicProblem,
            verification,
            timing,
            notification,
            checklist: {
                progressPercent: checklistProgress,
                totalSteps: problem.workChecklist?.steps?.length || 0,
                completedSteps: problem.workChecklist?.completedStepsCount || 0,
                isChecklistCompleted: problem.workChecklist?.isChecklistCompleted || false
            },
            images: {
                beforeImage: problem.beforeImage || '',
                afterImage: problem.afterImage || ''
            },
            yolo: {
                beforeDetections: problem.yoloDetections || [],
                afterDetections: problem.afterYoloDetections || [],
                modelName: problem.yoloModelName || '',
                inferenceMs: problem.yoloInferenceMs || 0
            }
        });

    } catch (error) {
        console.error('❌ [ADMIN VERIFICATION DETAIL] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 11. OVERRIDE VERIFICATION
// ═════════════════════════════════════════════
exports.overrideVerification = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const { verdict, note } = req.body;

        const validVerdicts = ['VERIFIED', 'PARTIAL', 'NOT_VERIFIED', 'UNVERIFIABLE'];
        if (!verdict || !validVerdicts.includes(verdict)) {
            return res.status(400).json({
                success: false,
                message: `verdict must be one of: ${validVerdicts.join(', ')}`
            });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }

        if (req.user.role === 'Admin' && problem.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        console.log(`⚡ [ADMIN VERIFICATION OVERRIDE] ${problem.title} → ${verdict}`);

        if (!problem.resolutionVerification) {
            problem.resolutionVerification = {};
        }

        problem.resolutionVerification.adminOverride = true;
        problem.resolutionVerification.adminOverrideBy = req.user._id;
        problem.resolutionVerification.adminOverrideAt = new Date();
        problem.resolutionVerification.adminOverrideNote = (note || `Manually set to ${verdict}`).slice(0, 300);
        problem.resolutionVerification.verdict = verdict;

        // Track whether we need to update community count
        const wasResolved = problem.status === 'Resolved';

        if (verdict === 'VERIFIED') {
            problem.status = 'Resolved';
            if (!problem.resolvedAt) problem.resolvedAt = new Date();
            problem.feedbackStatus = 'pending';
            problem.residentViewedAt = null;
            problem.statusHistory.push({
                status: 'Resolved',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: `Admin override: VERIFIED${note ? ` — ${note}` : ''}`
            });
        } else if (verdict === 'PARTIAL' || verdict === 'NOT_VERIFIED') {
            problem.status = 'In Progress';
            problem.resolvedAt = null;
            problem.statusHistory.push({
                status: 'In Progress',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: `Admin override: ${verdict}${note ? ` — ${note}` : ''}`
            });
        } else {
            problem.statusHistory.push({
                status: problem.status,
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: `Admin override: UNVERIFIABLE${note ? ` — ${note}` : ''}`
            });
        }

        await problem.save();

        // Update community resolvedCount if status changed
        const isNowResolved = problem.status === 'Resolved';
        if (!wasResolved && isNowResolved) {
            await incrementCommunityCount(problem.communityId, 'resolvedCount', 1);
        } else if (wasResolved && !isNowResolved) {
            await incrementCommunityCount(problem.communityId, 'resolvedCount', -1);
        }

        await createNotification({
            communityId: problem.communityId,
            recipientId: problem.reporterId,
            recipientEmail: problem.reporterEmail,
            category: verdict === 'VERIFIED' ? 'PROBLEM_RESOLVED' : 'PROBLEM_UPDATED',
            title: verdict === 'VERIFIED'
                ? '✅ Admin confirmed your issue is resolved'
                : `Report status: ${verdict}`,
            body: `"${problem.title}"${note ? ` — ${note}` : ''}`,
            relatedProblemId: problem._id,
            actorId: req.user._id,
            actorName: req.user.name,
            actorAvatar: req.user.avatarUrl || ''
        });

        if (problem.assignedTo) {
            await createNotification({
                communityId: problem.communityId,
                recipientId: problem.assignedTo,
                recipientEmail: 'maintainer@system',
                category: 'PROBLEM_UPDATED',
                title: 'Admin override on your task',
                body: `"${problem.title}" → ${verdict}`,
                relatedProblemId: problem._id,
                actorId: req.user._id,
                actorName: req.user.name
            });
        }

        res.status(200).json({
            success: true,
            message: `Verification overridden to ${verdict}`,
            verification: problem.resolutionVerification,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [ADMIN OVERRIDE] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 12. GET COMMUNITY SETTINGS
// ═════════════════════════════════════════════
exports.getCommunitySettings = async (req, res) => {
    try {
        if (!requireAdmin(req, res)) return;

        const communityId = req.user.role === 'SuperAdmin' && req.query.communityId
            ? req.query.communityId
            : req.user.communityId;

        const community = await Community.findById(communityId);
        if (!community) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        // Use toAdminJSON for masked settings
        res.status(200).json({
            success: true,
            community: community.toAdminJSON()
        });

    } catch (error) {
        console.error('❌ [ADMIN COMMUNITY] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 13. GET ALL CONFIG (masked) — SuperAdmin only
// ═════════════════════════════════════════════
exports.getAllConfig = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const configs = await Config.getAllMasked();

        res.status(200).json({
            success: true,
            configs
        });

    } catch (error) {
        console.error('❌ [ADMIN GET CONFIG] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 14. UPDATE CONFIG — SuperAdmin only
// ═════════════════════════════════════════════
// Body: { key, value, description? }
// ═════════════════════════════════════════════
exports.updateConfig = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const { key, value, description } = req.body;

        if (!key || value === undefined) {
            return res.status(400).json({ success: false, message: 'key and value are required' });
        }

        const validKeys = ['SMTP_CONFIG', 'GOOGLE_MAPS_API', 'OPENAI_API_KEY', 'GEMINI_CONFIG', 'YOLO_CONFIG', 'FIREBASE_CONFIG', 'APP_SETTINGS'];
        if (!validKeys.includes(key.toUpperCase())) {
            return res.status(400).json({ success: false, message: `Invalid config key. Must be one of: ${validKeys.join(', ')}` });
        }

        console.log(`\n⚙️ [ADMIN UPDATE CONFIG] ${key} by ${req.user.email}`);

        const config = await Config.setValue(
            key,
            value,
            req.user._id,
            req.user.name,
            description || `Updated via admin dashboard`,
            `Updated by ${req.user.name}`
        );

        // Reset transporter cache if SMTP changed
        if (key.toUpperCase() === 'SMTP_CONFIG') {
            try {
                resetTransporterCache();
                console.log('🔄 SMTP transporter cache cleared');
            } catch (err) {
                console.error('⚠️ Failed to reset transporter:', err.message);
            }
        }

        res.status(200).json({
            success: true,
            message: `${key} updated successfully`,
            config: config.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [ADMIN UPDATE CONFIG] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// 15. TEST SMTP CONFIG — SuperAdmin only
// ═════════════════════════════════════════════
exports.testSmtpConfig = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const { email } = req.body;
        if (!email) {
            return res.status(400).json({ success: false, message: 'Test email address is required' });
        }

        console.log(`\n📧 [ADMIN TEST SMTP] Sending test to ${email}`);

        await sendEmail({
            email,
            subject: 'Community AI — SMTP Test',
            message: 'This is a test email from Community AI. Your SMTP configuration is working!',
            html: `
                <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 30px; background: #F8FAFC; border-radius: 16px;">
                    <h1 style="color: #10B981;">✅ SMTP Test Successful</h1>
                    <p style="color: #0F172A;">Your SMTP configuration is working correctly.</p>
                    <p style="color: #64748B; font-size: 13px;">Sent at ${new Date().toISOString()}</p>
                </div>
            `
        });

        res.status(200).json({
            success: true,
            message: `Test email sent to ${email}`
        });

    } catch (error) {
        console.error('❌ [ADMIN TEST SMTP] Error:', error);
        res.status(500).json({
            success: false,
            message: `SMTP test failed: ${error.message}`
        });
    }
};

// ═════════════════════════════════════════════
// 16. GET COMMUNITY DETAIL (SuperAdmin only)
// ═════════════════════════════════════════════
exports.getCommunityDetail = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const community = await Community.findById(req.params.id);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        // Get recent stats
        const [totalProblems, resolvedProblems, totalUsers, totalMaintainers] = await Promise.all([
            Problem.countDocuments({ communityId: community._id, isDeleted: false }),
            Problem.countDocuments({ communityId: community._id, isDeleted: false, status: 'Resolved' }),
            User.countDocuments({ communityId: community._id, isActive: true }),
            User.countDocuments({ communityId: community._id, role: { $in: ['Staff', 'Admin'] }, isActive: true })
        ]);

        res.status(200).json({
            success: true,
            community: {
                ...community.toAdminJSON(),
                stats: {
                    totalProblems,
                    resolvedProblems,
                    totalUsers,
                    totalMaintainers,
                    resolutionRate: totalProblems > 0
                        ? Math.round((resolvedProblems / totalProblems) * 100)
                        : 0
                }
            }
        });

    } catch (error) {
        console.error('❌ [ADMIN COMMUNITY DETAIL] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};
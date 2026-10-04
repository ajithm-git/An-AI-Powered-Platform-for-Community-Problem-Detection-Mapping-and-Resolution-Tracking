const User = require('../models/User');
const Problem = require('../models/Problem');
const bcrypt = require('bcryptjs');

// ─────────────────────────────────────────────
// @desc    Get logged-in user's profile
// @route   GET /api/users/profile
// @access  Private (requires JWT)
// ─────────────────────────────────────────────
exports.getProfile = async (req, res) => {
    try {
        const user = await User.findById(req.user._id).select('-password -otp -resetPasswordOtp');

        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        return res.status(200).json({
            success: true,
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone,
                role: user.role,
                avatarUrl: user.avatarUrl || '',
                isVerified: user.isVerified,
                isActive: user.isActive,
                communityId: user.communityId || null,
                communityName: user.communityName || '',
                location: user.location || null,
                createdAt: user.createdAt
            }
        });
    } catch (error) {
        console.error('❌ [GET PROFILE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Update logged-in user's profile
// @route   PUT /api/users/profile
// @access  Private (requires JWT)
// Body: { name?, phone?, avatarUrl? }
// Note: email & role are NOT editable
// ─────────────────────────────────────────────
exports.updateProfile = async (req, res) => {
    try {
        const { name, phone, avatarUrl } = req.body;

        console.log(`\n📝 [UPDATE PROFILE] User: ${req.user.email}`);

        if (name !== undefined && (typeof name !== 'string' || name.trim().length < 2)) {
            return res.status(400).json({
                success: false,
                message: 'Name must be at least 2 characters'
            });
        }

        if (phone !== undefined) {
            const phoneRegex = /^[0-9+\-\s()]{7,20}$/;
            if (!phoneRegex.test(phone.trim())) {
                return res.status(400).json({
                    success: false,
                    message: 'Please provide a valid phone number'
                });
            }
        }

        if (avatarUrl !== undefined) {
            if (typeof avatarUrl !== 'string') {
                return res.status(400).json({
                    success: false,
                    message: 'avatarUrl must be a string'
                });
            }
            if (avatarUrl.length > 2_000_000) {
                return res.status(400).json({
                    success: false,
                    message: 'Avatar image is too large. Max ~1.5 MB.'
                });
            }
            if (avatarUrl.startsWith('data:') && !avatarUrl.startsWith('data:image/')) {
                return res.status(400).json({
                    success: false,
                    message: 'Avatar must be an image file'
                });
            }
        }

        const user = await User.findById(req.user._id);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        if (name !== undefined) user.name = name.trim();
        if (phone !== undefined) user.phone = phone.trim();
        if (avatarUrl !== undefined) user.avatarUrl = avatarUrl;

        await user.save();

        console.log(`✅ [UPDATE PROFILE] Profile updated for ${user.email}`);

        return res.status(200).json({
            success: true,
            message: 'Profile updated successfully',
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone,
                role: user.role,
                avatarUrl: user.avatarUrl || '',
                isVerified: user.isVerified,
                communityId: user.communityId || null,
                communityName: user.communityName || ''
            }
        });
    } catch (error) {
        console.error('❌ [UPDATE PROFILE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// AVATAR UPLOAD (multipart)
// ═════════════════════════════════════════════
// @desc    Upload a profile avatar (multipart/form-data)
// @route   POST /api/users/avatar
// @access  Private
// Accepts: `avatar` file part OR `avatarBase64` in body
// ═════════════════════════════════════════════
exports.uploadAvatar = async (req, res) => {
    try {
        console.log(`\n🖼️ [UPLOAD AVATAR] User: ${req.user.email}`);

        let avatarDataUri = null;

        // Option 1: multipart file (req.file from multer)
        if (req.file && req.file.buffer) {
            const mime = req.file.mimetype || 'image/jpeg';
            const base64 = req.file.buffer.toString('base64');
            avatarDataUri = `data:${mime};base64,${base64}`;
        }
        // Option 2: base64 in body
        else if (req.body && req.body.avatarBase64) {
            const raw = req.body.avatarBase64;
            avatarDataUri = raw.startsWith('data:')
                ? raw
                : `data:image/jpeg;base64,${raw}`;
        }

        if (!avatarDataUri) {
            return res.status(400).json({
                success: false,
                message: 'No avatar provided. Send `avatar` file or `avatarBase64` in body.'
            });
        }

        // Size limit ~2 MB (base64 inflates by ~33%, so ~1.5 MB original)
        if (avatarDataUri.length > 2_000_000) {
            return res.status(400).json({
                success: false,
                message: 'Avatar too large. Max ~1.5 MB after compression.'
            });
        }

        const user = await User.findById(req.user._id);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        user.avatarUrl = avatarDataUri;
        await user.save();

        console.log(`✅ [UPLOAD AVATAR] Avatar saved for ${user.email}`);

        return res.status(200).json({
            success: true,
            message: 'Avatar uploaded successfully',
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone,
                role: user.role,
                avatarUrl: user.avatarUrl,
                isVerified: user.isVerified,
                communityId: user.communityId || null,
                communityName: user.communityName || ''
            }
        });
    } catch (error) {
        console.error('❌ [UPLOAD AVATAR] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Change password (requires current password)
// @route   PUT /api/users/change-password
// @access  Private (requires JWT)
// Body: { currentPassword, newPassword }
// ⚠️ IMPORTANT: Do NOT manually hash. User model hashes on save.
// ─────────────────────────────────────────────
exports.changePassword = async (req, res) => {
    try {
        const { currentPassword, newPassword } = req.body;

        console.log(`\n🔐 [CHANGE PASSWORD] User: ${req.user.email}`);

        if (!currentPassword || !newPassword) {
            return res.status(400).json({
                success: false,
                message: 'Current password and new password are required'
            });
        }

        if (newPassword.length < 6) {
            return res.status(400).json({
                success: false,
                message: 'New password must be at least 6 characters'
            });
        }

        if (currentPassword === newPassword) {
            return res.status(400).json({
                success: false,
                message: 'New password must be different from current password'
            });
        }

        const user = await User.findById(req.user._id).select('+password');
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        const isMatch = await bcrypt.compare(currentPassword, user.password);
        if (!isMatch) {
            return res.status(400).json({
                success: false,
                message: 'Current password is incorrect'
            });
        }

        // ✅ Let the User model's pre('save') hook handle hashing
        user.password = newPassword;
        await user.save();

        console.log(`✅ [CHANGE PASSWORD] Password updated for ${user.email}`);
        return res.status(200).json({
            success: true,
            message: 'Password changed successfully'
        });
    } catch (error) {
        console.error('❌ [CHANGE PASSWORD] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// LOCATION UPDATE
// ═════════════════════════════════════════════
// @desc    Update the user's current GPS location
// @route   POST /api/users/location
// @access  Private
// Body: { latitude, longitude, accuracy? }
// ⚠️ Requires `location` field on User schema
// ═════════════════════════════════════════════
exports.updateLocation = async (req, res) => {
    try {
        const { latitude, longitude, accuracy } = req.body;

        console.log(`\n📍 [UPDATE LOCATION] User: ${req.user.email}`);

        if (typeof latitude !== 'number' || typeof longitude !== 'number') {
            return res.status(400).json({
                success: false,
                message: 'Valid latitude and longitude are required'
            });
        }

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return res.status(400).json({
                success: false,
                message: 'Latitude or longitude out of range'
            });
        }

        const user = await User.findById(req.user._id);
        if (!user) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        user.location = {
            latitude,
            longitude,
            accuracy: typeof accuracy === 'number' ? accuracy : null,
            updatedAt: new Date()
        };
        await user.save();

        console.log(`✅ [UPDATE LOCATION] ${user.email} → (${latitude.toFixed(4)}, ${longitude.toFixed(4)})`);

        return res.status(200).json({
            success: true,
            message: 'Location updated',
            location: user.location
        });
    } catch (error) {
        console.error('❌ [UPDATE LOCATION] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Get the user's current location
// @route   GET /api/users/location
// @access  Private
// ─────────────────────────────────────────────
exports.getLocation = async (req, res) => {
    try {
        const user = await User.findById(req.user._id).select('location');
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }
        return res.status(200).json({
            success: true,
            location: user.location || null
        });
    } catch (error) {
        console.error('❌ [GET LOCATION] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// Get maintainers (Staff + Admin) in my community
// ═════════════════════════════════════════════
// @desc    List all Staff and Admin users in my community
//          Used by Admin when assigning problems manually
// @route   GET /api/users/maintainers
// @access  Private (Staff/Admin/SuperAdmin)
// ═════════════════════════════════════════════
exports.getMaintainers = async (req, res) => {
    try {
        console.log(`\n👥 [GET MAINTAINERS] By: ${req.user.email} (role=${req.user.role})`);

        // Only Staff/Admin/SuperAdmin can see maintainer list
        if (!['Staff', 'Admin', 'SuperAdmin'].includes(req.user.role)) {
            return res.status(403).json({
                success: false,
                message: 'Access denied — Staff/Admin only'
            });
        }

        // Base filter — active staff + admin
        // Note: we do NOT require isVerified here, because admins may create
        // maintainers via dashboard who haven't confirmed their email yet.
        const filter = {
            role: { $in: ['Staff', 'Admin'] },
            isActive: true
        };

        // Scope by community
        if (req.user.role !== 'SuperAdmin') {
            filter.communityId = req.user.communityId;
        } else if (req.query.communityId) {
            filter.communityId = req.query.communityId;
        }

        const maintainers = await User.find(filter)
            .select('_id name email phone role avatarUrl communityId communityName isVerified')
            .sort({ name: 1 });

        // For each maintainer, count active + resolved tasks
        const enriched = await Promise.all(
            maintainers.map(async (m) => {
                const [activeTasks, resolvedTasks] = await Promise.all([
                    Problem.countDocuments({
                        assignedTo: m._id,
                        status: { $in: ['Assigned', 'In Progress'] },
                        isDeleted: false
                    }),
                    Problem.countDocuments({
                        assignedTo: m._id,
                        status: 'Resolved',
                        isDeleted: false
                    })
                ]);

                return {
                    id: m._id,
                    name: m.name,
                    email: m.email,
                    phone: m.phone || '',
                    role: m.role,
                    avatarUrl: m.avatarUrl || '',
                    communityId: m.communityId || null,
                    communityName: m.communityName || '',
                    isVerified: m.isVerified,
                    activeTasks,
                    resolvedTasks,
                    totalTasks: activeTasks + resolvedTasks
                };
            })
        );

        // Sort by fewest active tasks (least busy first)
        enriched.sort((a, b) => a.activeTasks - b.activeTasks);

        return res.status(200).json({
            success: true,
            count: enriched.length,
            maintainers: enriched
        });

    } catch (error) {
        console.error('❌ [GET MAINTAINERS] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};
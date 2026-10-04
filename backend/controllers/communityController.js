const Community = require('../models/Community');
const User = require('../models/User');
const Problem = require('../models/Problem');
const Notification = require('../models/Notification');

// ─────────────────────────────────────────────
// Permission helper: only SuperAdmin can manage communities
// ─────────────────────────────────────────────
function requireSuperAdmin(req, res) {
    if (req.user.role !== 'SuperAdmin') {
        res.status(403).json({
            success: false,
            message: 'Only SuperAdmin can perform this action'
        });
        return false;
    }
    return true;
}

// ─────────────────────────────────────────────
// Escape a string for safe use in RegExp
// ─────────────────────────────────────────────
function escapeRegex(str) {
    return String(str).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// ═════════════════════════════════════════════
// PUBLIC ENDPOINTS
// ═════════════════════════════════════════════

// ─────────────────────────────────────────────
// @desc    List active communities (for register dropdown)
// @route   GET /api/communities
// @access  Public
// ─────────────────────────────────────────────
exports.listActiveCommunities = async (req, res) => {
    try {
        const communities = await Community.find({
            isActive: true,
            isDeleted: false
        })
            .sort({ name: 1 })
            .select('name slug description address color memberCount problemCount resolvedCount logoUrl');

        return res.status(200).json({
            success: true,
            count: communities.length,
            communities: communities.map(c => c.toPublicJSON())
        });

    } catch (error) {
        console.error('❌ [LIST COMMUNITIES] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Get a single community by ID or slug
// @route   GET /api/communities/:idOrSlug
// @access  Public
// ─────────────────────────────────────────────
exports.getCommunity = async (req, res) => {
    try {
        const { idOrSlug } = req.params;

        let community = null;
        const isObjectId = /^[a-f\d]{24}$/i.test(idOrSlug);

        if (isObjectId) {
            community = await Community.findById(idOrSlug);
        }
        if (!community) {
            community = await Community.findOne({
                slug: String(idOrSlug).toLowerCase().trim()
            });
        }

        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        return res.status(200).json({
            success: true,
            community: community.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [GET COMMUNITY] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// SUPERADMIN ENDPOINTS
// ═════════════════════════════════════════════

// ─────────────────────────────────────────────
// @desc    Create a new community
// @route   POST /api/communities
// @access  Private (SuperAdmin only)
// Body: { name, description?, address?, contactEmail?, color? }
// ─────────────────────────────────────────────
exports.createCommunity = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const { name, description, address, contactEmail, color, logoUrl } = req.body;

        if (!name || name.trim().length < 2) {
            return res.status(400).json({
                success: false,
                message: 'Community name must be at least 2 characters'
            });
        }

        // Check for existing (case-insensitive, escaped)
        const existing = await Community.findOne({
            name: { $regex: new RegExp(`^${escapeRegex(name.trim())}$`, 'i') }
        });
        if (existing) {
            return res.status(400).json({
                success: false,
                message: 'A community with this name already exists'
            });
        }

        // Ensure unique slug BEFORE save
        const slug = await Community.generateUniqueSlug(name.trim());

        const community = new Community({
            name: name.trim(),
            slug,
            description: description?.trim() || '',
            address: address?.trim() || '',
            contactEmail: contactEmail?.trim().toLowerCase() || '',
            logoUrl: logoUrl || '',
            color: color || '#10B981',
            createdBy: req.user._id,
            createdByName: req.user.name,
            isActive: true
        });

        await community.save();

        console.log(`✅ [CREATE COMMUNITY] "${community.name}" by ${req.user.email}`);

        return res.status(201).json({
            success: true,
            message: 'Community created successfully',
            community: community.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [CREATE COMMUNITY] Error:', error);
        if (error.code === 11000) {
            return res.status(400).json({
                success: false,
                message: 'A community with this name or slug already exists'
            });
        }
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    List ALL communities (including inactive)
// @route   GET /api/communities/admin/all
// @access  Private (SuperAdmin only)
// ─────────────────────────────────────────────
exports.listAllCommunities = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        // Fast single query — no N+1 for member counts
        const communities = await Community.find({ isDeleted: false })
            .sort({ createdAt: -1 });

        const enriched = communities.map((c) => {
            const json = c.toPublicJSON();
            return {
                ...json,
                memberCount: c.memberCount || 0,
                problemCount: c.problemCount || 0,
                resolvedCount: c.resolvedCount || 0
            };
        });

        return res.status(200).json({
            success: true,
            count: enriched.length,
            communities: enriched
        });

    } catch (error) {
        console.error('❌ [LIST ALL COMMUNITIES] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Update a community
// @route   PUT /api/communities/:id
// @access  Private (SuperAdmin only)
// ─────────────────────────────────────────────
exports.updateCommunity = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const { name, description, address, contactEmail, color, isActive, logoUrl } = req.body;

        const community = await Community.findById(req.params.id);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        // If renaming, check for conflict + regenerate slug
        if (name && name.trim() !== community.name) {
            const existing = await Community.findOne({
                _id: { $ne: community._id },
                name: { $regex: new RegExp(`^${escapeRegex(name.trim())}$`, 'i') }
            });
            if (existing) {
                return res.status(400).json({
                    success: false,
                    message: 'Another community already has this name'
                });
            }

            community.name = name.trim();
            // Safely regenerate a unique slug
            community.slug = await Community.generateUniqueSlug(name.trim());
        }

        if (description !== undefined) community.description = description.trim();
        if (address !== undefined) community.address = address.trim();
        if (contactEmail !== undefined) community.contactEmail = contactEmail.trim().toLowerCase();
        if (logoUrl !== undefined) community.logoUrl = logoUrl;
        if (color !== undefined) community.color = color;
        if (typeof isActive === 'boolean') community.isActive = isActive;

        await community.save();

        console.log(`✅ [UPDATE COMMUNITY] "${community.name}" by ${req.user.email}`);

        return res.status(200).json({
            success: true,
            message: 'Community updated',
            community: community.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [UPDATE COMMUNITY] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Toggle community active/inactive
// @route   PATCH /api/communities/:id/toggle
// @access  Private (SuperAdmin only)
// ─────────────────────────────────────────────
exports.toggleCommunity = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const community = await Community.findById(req.params.id);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        community.isActive = !community.isActive;
        await community.save();

        console.log(`✅ [TOGGLE COMMUNITY] "${community.name}" → ${community.isActive ? 'ACTIVE' : 'INACTIVE'}`);

        // Notify all Admins + Staff in this community
        try {
            const admins = await User.find({
                communityId: community._id,
                role: { $in: ['Admin', 'Staff'] },
                isActive: true
            }).select('_id email');

            for (const admin of admins) {
                await Notification.create({
                    communityId: community._id,
                    recipientId: admin._id,
                    recipientEmail: admin.email,
                    category: community.isActive ? 'COMMUNITY_UPDATED' : 'COMMUNITY_DEACTIVATED',
                    title: community.isActive
                        ? `${community.name} is now active`
                        : `${community.name} has been deactivated`,
                    body: community.isActive
                        ? 'Your community is visible to new users again.'
                        : 'Your community has been hidden from new registrations.',
                    relatedCommunityId: community._id,
                    actorId: req.user._id,
                    actorName: req.user.name
                });
            }
        } catch (notifyErr) {
            console.error('⚠️ Notification failed:', notifyErr.message);
        }

        return res.status(200).json({
            success: true,
            message: `Community ${community.isActive ? 'activated' : 'deactivated'}`,
            community: community.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [TOGGLE COMMUNITY] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Soft delete a community
// @route   DELETE /api/communities/:id
// @access  Private (SuperAdmin only)
// ─────────────────────────────────────────────
exports.deleteCommunity = async (req, res) => {
    try {
        if (!requireSuperAdmin(req, res)) return;

        const community = await Community.findById(req.params.id);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        // Safety: block deletion if members still exist
        const memberCount = await User.countDocuments({
            communityId: community._id,
            isActive: true
        });

        if (memberCount > 0) {
            return res.status(400).json({
                success: false,
                message: `Cannot delete — ${memberCount} active member(s) still belong to this community. Deactivate it instead, or reassign members first.`
            });
        }

        community.isDeleted = true;
        community.isActive = false;
        await community.save();

        console.log(`🗑️ [DELETE COMMUNITY] "${community.name}" by ${req.user.email}`);

        return res.status(200).json({
            success: true,
            message: 'Community deleted'
        });

    } catch (error) {
        console.error('❌ [DELETE COMMUNITY] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// NEW — Get community stats (for admin dashboard)
// ═════════════════════════════════════════════
// @desc    Get aggregated stats for a community
// @route   GET /api/communities/:id/stats
// @access  Private (SuperAdmin / community Admin)
// ═════════════════════════════════════════════
exports.getCommunityStats = async (req, res) => {
    try {
        const communityId = req.params.id;

        if (req.user.role !== 'SuperAdmin' &&
            req.user.communityId?.toString() !== communityId) {
            return res.status(403).json({
                success: false,
                message: 'Access denied — different community'
            });
        }

        const community = await Community.findById(communityId);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Community not found' });
        }

        const [
            totalUsers,
            totalResidents,
            totalStaff,
            totalProblems,
            resolvedProblems,
            openProblems
        ] = await Promise.all([
            User.countDocuments({ communityId, isActive: true }),
            User.countDocuments({ communityId, role: 'Resident', isActive: true }),
            User.countDocuments({ communityId, role: { $in: ['Staff', 'Admin'] }, isActive: true }),
            Problem.countDocuments({ communityId, isDeleted: false }),
            Problem.countDocuments({ communityId, isDeleted: false, status: 'Resolved' }),
            Problem.countDocuments({
                communityId,
                isDeleted: false,
                status: { $in: ['Open', 'Assigned', 'In Progress'] }
            })
        ]);

        return res.status(200).json({
            success: true,
            stats: {
                communityId: community._id,
                communityName: community.name,
                users: {
                    total: totalUsers,
                    residents: totalResidents,
                    staff: totalStaff
                },
                problems: {
                    total: totalProblems,
                    resolved: resolvedProblems,
                    open: openProblems,
                    resolutionRate: totalProblems > 0
                        ? Math.round((resolvedProblems / totalProblems) * 100)
                        : 0
                }
            }
        });

    } catch (error) {
        console.error('❌ [GET COMMUNITY STATS] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};
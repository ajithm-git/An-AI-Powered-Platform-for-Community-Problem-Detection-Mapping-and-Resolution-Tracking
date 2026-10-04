const Message = require('../models/Message');
const User = require('../models/User');
const Notification = require('../models/Notification');
const Community = require('../models/Community');
const mongoose = require('mongoose');

// ─────────────────────────────────────────────
// Room templates — composed per community
// Final room ID = `${communitySlug}__${templateId}`
// ─────────────────────────────────────────────
const ROOM_TEMPLATES = [
    {
        id: 'community-general',
        name: 'Community General',
        description: 'All members of this community',
        staffOnly: false
    },
    {
        id: 'building-a',
        name: 'Building A',
        description: 'Building A residents',
        staffOnly: false
    },
    {
        id: 'building-b',
        name: 'Building B',
        description: 'Building B residents',
        staffOnly: false
    },
    {
        id: 'maintenance-team',
        name: 'Maintenance Team',
        description: 'Staff + Admin only',
        staffOnly: true
    },
    {
        id: 'announcements',
        name: 'Announcements',
        description: 'Read-only community updates',
        staffOnly: false,
        postAdminOnly: true
    }
];

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

function composeRoomId(communitySlug, templateId) {
    return `${communitySlug}__${templateId}`;
}

function parseRoomId(roomId) {
    const idx = roomId.indexOf('__');
    if (idx === -1) return { communitySlug: null, templateId: roomId };
    return {
        communitySlug: roomId.slice(0, idx),
        templateId: roomId.slice(idx + 2)
    };
}

function getRoomTemplate(templateId) {
    return ROOM_TEMPLATES.find(r => r.id === templateId);
}

function checkRoomPermission(user, template) {
    if (!template) return { allowed: false, reason: 'Unknown room' };
    if (template.staffOnly && user.role === 'Resident') {
        return { allowed: false, reason: 'Only staff can access this room' };
    }
    return { allowed: true };
}

function checkPostPermission(user, template) {
    const base = checkRoomPermission(user, template);
    if (!base.allowed) return base;

    if (template.postAdminOnly && !['Admin', 'SuperAdmin', 'Staff'].includes(user.role)) {
        return { allowed: false, reason: 'Only Admin/Staff can post here' };
    }
    return { allowed: true };
}

/**
 * Build a deterministic direct-message room ID for two users.
 * Uses sorted IDs so "A→B" and "B→A" produce the same room.
 */
function buildDirectRoomId(userId1, userId2) {
    const sorted = [String(userId1), String(userId2)].sort();
    return `direct__${sorted[0]}_${sorted[1]}`;
}

function isValidObjectId(id) {
    return mongoose.Types.ObjectId.isValid(id);
}

// ═════════════════════════════════════════════
// @desc    Get list of chat rooms for user's community
// @route   GET /api/messages/rooms
// @access  Private
// ═════════════════════════════════════════════
exports.getRooms = async (req, res) => {
    try {
        // SuperAdmin has no community — return a hint
        if (!req.user.communityId) {
            return res.status(200).json({
                success: true,
                rooms: [],
                directRooms: [],
                message: 'No community assigned'
            });
        }

        const community = req.user.community || await Community.findById(req.user.communityId);
        if (!community) {
            return res.status(404).json({
                success: false,
                message: 'Community not found'
            });
        }

        const slug = community.slug;

        // ─── ROOM MESSAGES ─────────────────────
        const roomsWithMeta = await Promise.all(
            ROOM_TEMPLATES
                .filter(t => !t.staffOnly || ['Staff', 'Admin', 'SuperAdmin'].includes(req.user.role))
                .map(async (template) => {
                    const fullRoomId = composeRoomId(slug, template.id);

                    const [lastMsg, count, unreadCount] = await Promise.all([
                        Message.findOne({
                            communityId: community._id,
                            room: fullRoomId,
                            isDeleted: false
                        })
                            .sort({ createdAt: -1 })
                            .limit(1),
                        Message.countDocuments({
                            communityId: community._id,
                            room: fullRoomId,
                            isDeleted: false
                        }),
                        Message.countDocuments({
                            communityId: community._id,
                            room: fullRoomId,
                            isDeleted: false,
                            readBy: { $ne: req.user._id },
                            senderId: { $ne: req.user._id }
                        })
                    ]);

                    return {
                        id: fullRoomId,
                        name: template.name,
                        description: template.description,
                        type: 'room',
                        messageCount: count,
                        unreadCount,
                        lastMessage: lastMsg ? {
                            text: lastMsg.text?.slice(0, 80) || '[image]',
                            senderName: lastMsg.senderName,
                            createdAt: lastMsg.createdAt
                        } : null
                    };
                })
        );

        // ─── DIRECT MESSAGES ───────────────────
        // Find recent direct-message partners (distinct room IDs)
        const directRoomsAgg = await Message.aggregate([
            {
                $match: {
                    type: 'direct',
                    isDeleted: false,
                    $or: [
                        { senderId: req.user._id },
                        { recipientId: req.user._id }
                    ]
                }
            },
            { $sort: { createdAt: -1 } },
            {
                $group: {
                    _id: '$room',
                    lastMessage: { $first: '$$ROOT' }
                }
            },
            { $limit: 20 }
        ]);

        const directRooms = await Promise.all(
            directRoomsAgg.map(async (entry) => {
                const last = entry.lastMessage;
                const otherId = last.senderId.toString() === req.user._id.toString()
                    ? last.recipientId
                    : last.senderId;

                let otherUser = null;
                if (isValidObjectId(otherId)) {
                    otherUser = await User.findById(otherId)
                        .select('name role avatarUrl')
                        .lean();
                }

                const unread = await Message.countDocuments({
                    room: entry._id,
                    isDeleted: false,
                    recipientId: req.user._id,
                    readBy: { $ne: req.user._id }
                });

                return {
                    id: entry._id,
                    name: otherUser?.name || 'Direct chat',
                    description: otherUser?.role || 'Direct message',
                    type: 'direct',
                    avatarUrl: otherUser?.avatarUrl || '',
                    otherUserId: otherId,
                    unreadCount: unread,
                    lastMessage: last ? {
                        text: last.text?.slice(0, 80) || '[image]',
                        senderName: last.senderName,
                        createdAt: last.createdAt
                    } : null
                };
            })
        );

        return res.status(200).json({
            success: true,
            communityId: community._id,
            communityName: community.name,
            rooms: roomsWithMeta,
            directRooms
        });

    } catch (error) {
        console.error('❌ [GET ROOMS] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Get messages in a room (paginated)
// @route   GET /api/messages/:room?limit=50&before=<messageId>
// @access  Private
// ═════════════════════════════════════════════
exports.getMessages = async (req, res) => {
    try {
        const { room } = req.params;
        const limit = Math.min(parseInt(req.query.limit) || 50, 100);
        const before = req.query.before;

        const { communitySlug, templateId } = parseRoomId(room);

        // ─── DIRECT ROOM PATH ──────────────────
        if (templateId.startsWith('direct__')) {
            // Direct room IDs start with "direct__" — but here
            // the room is already the full ID.
            // Just verify the user is a participant.
            const isParticipant = room.includes(String(req.user._id));
            if (!isParticipant) {
                return res.status(403).json({
                    success: false,
                    message: 'You are not a participant in this conversation'
                });
            }
        } else {
            // ─── COMMUNITY ROOM PATH ───────────
            const community = req.user.community || await Community.findById(req.user.communityId);
            if (!community || community.slug !== communitySlug) {
                return res.status(403).json({
                    success: false,
                    message: 'This chat belongs to a different community'
                });
            }

            const template = getRoomTemplate(templateId);
            const perm = checkRoomPermission(req.user, template);
            if (!perm.allowed) {
                return res.status(403).json({ success: false, message: perm.reason });
            }
        }

        const filter = {
            room: room.toLowerCase(),
            isDeleted: false
        };
        if (before && isValidObjectId(before)) {
            filter._id = { $lt: before };
        }

        const messages = await Message.find(filter)
            .sort({ createdAt: -1 })
            .limit(limit);

        const sorted = messages.reverse();

        return res.status(200).json({
            success: true,
            room,
            count: sorted.length,
            messages: sorted.map(m => m.toPublicJSON(req.user._id))
        });

    } catch (error) {
        console.error('❌ [GET MESSAGES] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Send a message
// @route   POST /api/messages/:room
// @access  Private
// Body: { text?, imageBase64?, relatedProblemId? }
// ═════════════════════════════════════════════
exports.sendMessage = async (req, res) => {
    try {
        const { room } = req.params;
        const { text, imageBase64, relatedProblemId } = req.body;

        if ((!text || !text.trim()) && !imageBase64) {
            return res.status(400).json({
                success: false,
                message: 'Message text or image is required'
            });
        }

        const { communitySlug, templateId } = parseRoomId(room);

        let community = null;
        let messageType = 'room';
        let recipientId = null;

        // ─── DIRECT MESSAGE PATH ───────────────
        if (room.startsWith('direct__')) {
            // Extract the two user IDs from the room name
            const idsPart = room.replace('direct__', '');
            const [id1, id2] = idsPart.split('_');

            if (!isValidObjectId(id1) || !isValidObjectId(id2)) {
                return res.status(400).json({
                    success: false,
                    message: 'Invalid direct room ID format'
                });
            }

            const myId = String(req.user._id);
            if (myId !== id1 && myId !== id2) {
                return res.status(403).json({
                    success: false,
                    message: 'You are not a participant in this conversation'
                });
            }

            recipientId = myId === id1 ? id2 : id1;

            const recipient = await User.findById(recipientId).select('_id communityId');
            if (!recipient) {
                return res.status(404).json({
                    success: false,
                    message: 'Recipient not found'
                });
            }

            // Direct messages link to the sender's community
            community = req.user.community || await Community.findById(req.user.communityId);
            messageType = 'direct';
        } else {
            // ─── COMMUNITY ROOM PATH ───────────
            community = req.user.community || await Community.findById(req.user.communityId);
            if (!community || community.slug !== communitySlug) {
                return res.status(403).json({
                    success: false,
                    message: 'This chat belongs to a different community'
                });
            }

            const template = getRoomTemplate(templateId);
            const perm = checkPostPermission(req.user, template);
            if (!perm.allowed) {
                return res.status(403).json({ success: false, message: perm.reason });
            }
        }

        // ─── Size guard for images ─────────────
        if (imageBase64 && imageBase64.length > 1_500_000) {
            return res.status(400).json({
                success: false,
                message: 'Image too large. Max ~1 MB.'
            });
        }

        const message = new Message({
            communityId: community?._id || null,
            room: room.toLowerCase(),
            type: messageType,
            senderId: req.user._id,
            senderName: req.user.name,
            senderRole: req.user.role,
            senderAvatar: req.user.avatarUrl || '',
            recipientId,
            text: text ? text.trim() : '',
            imageBase64: imageBase64 || '',
            relatedProblemId: relatedProblemId || null,
            readBy: [req.user._id]
        });

        await message.save();

        console.log(`💬 [${room}] ${req.user.name}: ${(text || '[image]').slice(0, 60)}`);

        // ─── Notify recipient for direct messages ───
        if (messageType === 'direct' && recipientId) {
            try {
                await Notification.create({
                    communityId: community?._id || null,
                    recipientId,
                    recipientEmail: '',
                    category: 'CHAT_MESSAGE',
                    title: `New message from ${req.user.name}`,
                    body: text ? text.slice(0, 80) : '[image]',
                    relatedUserId: req.user._id,
                    actorId: req.user._id,
                    actorName: req.user.name,
                    actorAvatar: req.user.avatarUrl || ''
                });
            } catch (err) {
                console.error('⚠️ Chat notification failed:', err.message);
            }
        }

        return res.status(201).json({
            success: true,
            message: 'Message sent',
            data: message.toPublicJSON(req.user._id)
        });

    } catch (error) {
        console.error('❌ [SEND MESSAGE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Mark all messages in a room as read
// @route   PATCH /api/messages/:room/read
// @access  Private
// ═════════════════════════════════════════════
exports.markRoomRead = async (req, res) => {
    try {
        const { room } = req.params;

        // Verify access for community rooms
        if (!room.startsWith('direct__')) {
            const { communitySlug } = parseRoomId(room);
            const community = req.user.community || await Community.findById(req.user.communityId);
            if (!community || community.slug !== communitySlug) {
                return res.status(403).json({
                    success: false,
                    message: 'This chat belongs to a different community'
                });
            }
        } else if (!room.includes(String(req.user._id))) {
            return res.status(403).json({
                success: false,
                message: 'You are not a participant in this conversation'
            });
        }

        const result = await Message.updateMany(
            {
                room: room.toLowerCase(),
                readBy: { $ne: req.user._id },
                isDeleted: false
            },
            { $addToSet: { readBy: req.user._id } }
        );

        return res.status(200).json({
            success: true,
            message: 'Room marked as read',
            modifiedCount: result.modifiedCount || 0
        });

    } catch (error) {
        console.error('❌ [MARK ROOM READ] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Delete own message
// @route   DELETE /api/messages/message/:id
// @access  Private (sender or Admin — same community)
// ═════════════════════════════════════════════
exports.deleteMessage = async (req, res) => {
    try {
        if (!isValidObjectId(req.params.id)) {
            return res.status(400).json({
                success: false,
                message: 'Invalid message ID'
            });
        }

        const message = await Message.findById(req.params.id);
        if (!message || message.isDeleted) {
            return res.status(404).json({ success: false, message: 'Message not found' });
        }

        const isSuperAdmin = req.user.role === 'SuperAdmin';
        const sameCommunity = message.communityId?.toString() === req.user.communityId?.toString();
        if (!isSuperAdmin && !sameCommunity) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isSender = message.senderId.toString() === req.user._id.toString();
        const isAdmin = ['Admin', 'SuperAdmin'].includes(req.user.role);

        if (!isSender && !isAdmin) {
            return res.status(403).json({
                success: false,
                message: 'Only the sender or an Admin can delete this message'
            });
        }

        message.isDeleted = true;
        await message.save();

        return res.status(200).json({ success: true, message: 'Message deleted' });

    } catch (error) {
        console.error('❌ [DELETE MESSAGE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Unread message count
// @route   GET /api/messages/unread-count
// @access  Private
// ═════════════════════════════════════════════
exports.getUnreadCount = async (req, res) => {
    try {
        if (!req.user.communityId) {
            return res.status(200).json({ success: true, unreadCount: 0 });
        }

        // Community room unread
        const roomUnread = await Message.countDocuments({
            communityId: req.user.communityId,
            type: { $in: ['room', 'announcement'] },
            readBy: { $ne: req.user._id },
            isDeleted: false,
            senderId: { $ne: req.user._id }
        });

        // Direct unread
        const directUnread = await Message.countDocuments({
            type: 'direct',
            recipientId: req.user._id,
            readBy: { $ne: req.user._id },
            isDeleted: false
        });

        return res.status(200).json({
            success: true,
            unreadCount: roomUnread + directUnread,
            roomUnread,
            directUnread
        });

    } catch (error) {
        console.error('❌ [UNREAD COUNT] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// @desc    Start (or get) a direct message room with another user
// @route   POST /api/messages/direct/start
// @access  Private
// Body: { userId }
// ═════════════════════════════════════════════
exports.startDirectRoom = async (req, res) => {
    try {
        const { userId } = req.body;

        if (!isValidObjectId(userId)) {
            return res.status(400).json({
                success: false,
                message: 'Valid userId is required'
            });
        }

        if (String(userId) === String(req.user._id)) {
            return res.status(400).json({
                success: false,
                message: 'You cannot message yourself'
            });
        }

        const recipient = await User.findById(userId).select('name role avatarUrl communityId');
        if (!recipient) {
            return res.status(404).json({
                success: false,
                message: 'User not found'
            });
        }

        // Prevent cross-community DM for non-SuperAdmin
        if (req.user.role !== 'SuperAdmin' &&
            recipient.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({
                success: false,
                message: 'Cannot message users from a different community'
            });
        }

        const roomId = buildDirectRoomId(req.user._id, userId);

        return res.status(200).json({
            success: true,
            room: {
                id: roomId,
                name: recipient.name,
                role: recipient.role,
                avatarUrl: recipient.avatarUrl || '',
                otherUserId: recipient._id
            }
        });

    } catch (error) {
        console.error('❌ [START DIRECT ROOM] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};
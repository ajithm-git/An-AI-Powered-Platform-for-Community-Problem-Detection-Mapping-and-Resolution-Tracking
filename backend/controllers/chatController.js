const { chatReply, isGeminiConfigured } = require('../utils/geminiService');

// ─────────────────────────────────────────────
// Simple in-memory rate limiter (per user)
// ─────────────────────────────────────────────
const rateLimiter = new Map(); // userId → { count, resetAt }
const RATE_LIMIT_WINDOW_MS = 60 * 1000;   // 1 minute
const RATE_LIMIT_MAX_MESSAGES = 20;       // 20 messages per minute per user

function checkRateLimit(userId) {
    const now = Date.now();
    const record = rateLimiter.get(userId) || { count: 0, resetAt: now + RATE_LIMIT_WINDOW_MS };

    if (now > record.resetAt) {
        record.count = 0;
        record.resetAt = now + RATE_LIMIT_WINDOW_MS;
    }

    record.count++;
    rateLimiter.set(userId, record);

    return {
        allowed: record.count <= RATE_LIMIT_MAX_MESSAGES,
        remaining: Math.max(0, RATE_LIMIT_MAX_MESSAGES - record.count),
        resetInMs: record.resetAt - now
    };
}

// Cleanup stale records every 5 minutes
setInterval(() => {
    const now = Date.now();
    for (const [userId, record] of rateLimiter.entries()) {
        if (now > record.resetAt) rateLimiter.delete(userId);
    }
}, 5 * 60 * 1000);

// ─────────────────────────────────────────────
// Simple in-memory FAQ cache (helps avoid Gemini calls for common questions)
// ─────────────────────────────────────────────
const FAQ_CACHE = new Map();
const FAQ_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

function getCachedReply(message) {
    const key = String(message).toLowerCase().trim().slice(0, 100);
    const entry = FAQ_CACHE.get(key);
    if (!entry) return null;
    if (Date.now() - entry.cachedAt > FAQ_TTL_MS) {
        FAQ_CACHE.delete(key);
        return null;
    }
    return entry.reply;
}

function setCachedReply(message, reply) {
    const key = String(message).toLowerCase().trim().slice(0, 100);
    FAQ_CACHE.set(key, { reply, cachedAt: Date.now() });
    // Keep cache bounded
    if (FAQ_CACHE.size > 200) {
        const firstKey = FAQ_CACHE.keys().next().value;
        FAQ_CACHE.delete(firstKey);
    }
}

// Common FAQ keys — only cache for these
const FAQ_PATTERNS = [
    /how (do i|to) report/i,
    /what is community ai/i,
    /how does (yolo|verification|ai) work/i,
    /forgot password/i,
    /how to (reset|change) password/i,
    /what is my role/i,
    /how to contact (admin|support)/i
];

function isFaqQuestion(message) {
    return FAQ_PATTERNS.some(re => re.test(message));
}

// ═════════════════════════════════════════════
// Send a message to the AI chatbot
// ═════════════════════════════════════════════
// @desc    Send a message to the AI chatbot
// @route   POST /api/chat/message
// @access  Private (JWT)
// Body: { message: string, history: [{role, text}] }
// ═════════════════════════════════════════════
exports.sendChatMessage = async (req, res) => {
    try {
        const { message, history } = req.body;

        // ─────────────────────────────────────
        // Validation
        // ─────────────────────────────────────
        if (!message || typeof message !== 'string' || !message.trim()) {
            return res.status(400).json({
                success: false,
                message: 'Message is required'
            });
        }

        if (message.length > 2000) {
            return res.status(400).json({
                success: false,
                message: 'Message too long (max 2000 characters)'
            });
        }

        if (!isGeminiConfigured()) {
            return res.status(503).json({
                success: false,
                message: 'AI assistant is not available right now. Please try again later.'
            });
        }

        // ─────────────────────────────────────
        // Rate limiting
        // ─────────────────────────────────────
        const userId = String(req.user?._id || req.ip);
        const rate = checkRateLimit(userId);

        if (!rate.allowed) {
            const seconds = Math.ceil(rate.resetInMs / 1000);
            return res.status(429).json({
                success: false,
                message: `Slow down! You can send another message in ${seconds}s.`
            });
        }

        const cleanMessage = message.trim();

        // ─────────────────────────────────────
        // Check FAQ cache
        // ─────────────────────────────────────
        if (isFaqQuestion(cleanMessage)) {
            const cached = getCachedReply(cleanMessage);
            if (cached) {
                console.log(`💬 [CHAT] Cache hit: ${cleanMessage.slice(0, 60)}`);
                return res.status(200).json({
                    success: true,
                    reply: cached,
                    cached: true,
                    timestamp: new Date().toISOString(),
                    rateLimitRemaining: rate.remaining
                });
            }
        }

        // ─────────────────────────────────────
        // Sanitize + normalize history
        // ─────────────────────────────────────
        const safeHistory = Array.isArray(history)
            ? history
                .filter(h =>
                    h &&
                    typeof h.text === 'string' &&
                    ['user', 'model', 'assistant'].includes(h.role)
                )
                .slice(-20)
                .map(h => ({
                    role: h.role === 'assistant' ? 'model' : h.role,
                    text: String(h.text).slice(0, 1000)
                }))
            : [];

        // ─────────────────────────────────────
        // Call Gemini
        // ─────────────────────────────────────
        const userContext = {
            name: req.user?.name || 'User',
            role: req.user?.role || 'Resident'
        };

        console.log(`\n💬 [CHAT] ${userContext.name} (${userContext.role}): ${cleanMessage.slice(0, 80)}`);
        console.log(`   History: ${safeHistory.length} turn(s)`);

        const reply = await chatReply(cleanMessage, safeHistory, userContext);

        console.log(`🤖 [CHAT] Reply: ${reply.slice(0, 80)}`);

        // ─────────────────────────────────────
        // Cache FAQ replies
        // ─────────────────────────────────────
        if (isFaqQuestion(cleanMessage) && reply.length < 1000) {
            setCachedReply(cleanMessage, reply);
        }

        return res.status(200).json({
            success: true,
            reply,
            cached: false,
            timestamp: new Date().toISOString(),
            rateLimitRemaining: rate.remaining
        });

    } catch (error) {
        console.error('❌ [CHAT] Error:', error);
        return res.status(500).json({
            success: false,
            message: error.message || 'Chat failed. Please try again.'
        });
    }
};

// ═════════════════════════════════════════════
// Chat status
// ═════════════════════════════════════════════
// @desc    Health check — is Gemini configured?
// @route   GET /api/chat/status
// @access  Private (JWT)
// ═════════════════════════════════════════════
exports.getChatStatus = async (req, res) => {
    return res.status(200).json({
        success: true,
        aiAvailable: isGeminiConfigured(),
        provider: 'Google Gemini',
        model: process.env.GEMINI_MODEL || 'gemini-1.5-flash-latest'
    });
};

// ═════════════════════════════════════════════
// Welcome message (no AI call)
// ═════════════════════════════════════════════
// @desc    Get a personalized welcome message
// @route   GET /api/chat/welcome
// @access  Private (JWT)
// ═════════════════════════════════════════════
exports.getWelcome = async (req, res) => {
    try {
        const name = req.user?.name?.split(' ')[0] || 'there';
        const role = req.user?.role || 'Resident';

        const greetingByRole = {
            'Resident': `Hi ${name}! I can help you report problems, check status, or answer questions about the app. What's on your mind?`,
            'Staff': `Hey ${name}! Ready to tackle some tasks? I can help you understand a problem, review the work checklist, or answer app questions.`,
            'Admin': `Hello ${name}! I'm here to help you manage the community. Ask me about problems, users, or how any feature works.`,
            'SuperAdmin': `Welcome ${name}! Ask me anything about the platform, communities, or AI configuration.`
        };

        return res.status(200).json({
            success: true,
            reply: greetingByRole[role] || greetingByRole['Resident'],
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('❌ [CHAT WELCOME] Error:', error);
        return res.status(500).json({
            success: false,
            message: error.message || 'Failed to load welcome message'
        });
    }
};

// ═════════════════════════════════════════════
// Test endpoint (public) — verifies Gemini works
// ═════════════════════════════════════════════
// @desc    Test endpoint (public) — verifies Gemini works
// @route   GET /api/chat/test
// @access  Public
// ═════════════════════════════════════════════
exports.testChat = async (req, res) => {
    try {
        if (!isGeminiConfigured()) {
            return res.status(503).json({
                success: false,
                message: 'Gemini API key not configured in .env'
            });
        }

        const reply = await chatReply(
            'Say hello to the Community AI developers in one short sentence.',
            [],
            { name: 'Developer', role: 'Admin' }
        );

        return res.status(200).json({
            success: true,
            message: 'Gemini is working!',
            reply
        });

    } catch (error) {
        console.error('❌ [CHAT TEST] Error:', error);
        return res.status(500).json({
            success: false,
            message: error.message
        });
    }
};
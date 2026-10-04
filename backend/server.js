/**
 * Community AI Backend Service
 * Node.js + Express + MongoDB (Local) + SMTP Nodemailer + Gemini AI + YOLO
 *
 * Architecture:
 * - config/db.js          → MongoDB connection
 * - models/*              → Schemas (User, Community, Problem, Notification, Config)
 * - controllers/*         → Business logic
 * - middleware/*          → JWT auth middleware
 * - routes/*              → API endpoints
 * - utils/*               → emailService, geminiService, yoloService
 */

require('dotenv').config();
const express = require('express');
const cors = require('cors');
const mongoose = require('mongoose');
const connectDB = require('./config/db');
const { ensureSuperAdmin } = require('./controllers/authController');

const app = express();
const PORT = process.env.PORT || 5000;

// ─────────────────────────────────────────────
// 1. Connect to MongoDB + Seed SuperAdmin
// ─────────────────────────────────────────────
connectDB().then(async () => {
    // Seed SuperAdmin after DB connection succeeds
    try {
        await ensureSuperAdmin();
    } catch (err) {
        console.error('⚠️ SuperAdmin seed failed:', err.message);
    }
});

// ─────────────────────────────────────────────
// 2. Global Middleware
// ─────────────────────────────────────────────
app.use(cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
    exposedHeaders: ['Authorization']
}));

// 15 MB JSON body — needed for base64 avatars and problem photos
app.use(express.json({ limit: '15mb' }));
app.use(express.urlencoded({ extended: true, limit: '15mb' }));

// Request logger
app.use((req, res, next) => {
    const start = Date.now();
    res.on('finish', () => {
        const duration = Date.now() - start;
        console.log(`📥 ${req.method} ${req.originalUrl} → ${res.statusCode} (${duration}ms)`);
    });
    next();
});

// ─────────────────────────────────────────────
// 3. Rate Limiting (simple, no external dep)
// ─────────────────────────────────────────────
const rateLimitStore = new Map();
const RATE_LIMIT_WINDOW_MS = 60 * 1000; // 1 minute
const RATE_LIMIT_MAX_REQUESTS = 120;    // 120 requests per minute per IP

app.use((req, res, next) => {
    // Skip rate limiting for health checks
    if (req.path === '/api/health' || req.path === '/') return next();

    const ip = req.ip || req.connection.remoteAddress;
    const now = Date.now();
    const record = rateLimitStore.get(ip) || { count: 0, resetAt: now + RATE_LIMIT_WINDOW_MS };

    if (now > record.resetAt) {
        record.count = 0;
        record.resetAt = now + RATE_LIMIT_WINDOW_MS;
    }

    record.count++;
    rateLimitStore.set(ip, record);

    if (record.count > RATE_LIMIT_MAX_REQUESTS) {
        return res.status(429).json({
            success: false,
            message: 'Too many requests. Please try again later.'
        });
    }
    next();
});

// Periodic cleanup of rate limit store
setInterval(() => {
    const now = Date.now();
    for (const [ip, record] of rateLimitStore.entries()) {
        if (now > record.resetAt) rateLimitStore.delete(ip);
    }
}, 5 * 60 * 1000); // every 5 min

// ─────────────────────────────────────────────
// 4. Mount Routes (with safe loading)
// ─────────────────────────────────────────────
function mountRoute(path, routePath) {
    try {
        const router = require(routePath);
        app.use(path, router);
        console.log(`   ✅ Mounted ${path}`);
    } catch (err) {
        console.error(`   ❌ Failed to mount ${path}:`, err.message);
    }
}

// Mount in order
mountRoute('/api/auth', './routes/authRoutes');
mountRoute('/api/users', './routes/userRoutes');
mountRoute('/api/communities', './routes/communityRoutes');
mountRoute('/api/problems', './routes/problemRoutes');
mountRoute('/api/notifications', './routes/notificationRoutes');
mountRoute('/api/messages', './routes/messageRoutes');
mountRoute('/api/chat', './routes/chatRoutes');
mountRoute('/api/admin', './routes/adminRoutes');

// ─────────────────────────────────────────────
// 5. Root route
// ─────────────────────────────────────────────
app.get('/', (req, res) => {
    res.json({
        success: true,
        message: 'Community AI Backend is running',
        version: '3.1.0',
        endpoints: {
            auth: '/api/auth',
            users: '/api/users',
            communities: '/api/communities',
            problems: '/api/problems',
            notifications: '/api/notifications',
            messages: '/api/messages',
            chat: '/api/chat',
            admin: '/api/admin',
            health: '/api/health'
        }
    });
});

// ─────────────────────────────────────────────
// 6. Health Check
// ─────────────────────────────────────────────
app.get('/api/health', async (req, res) => {
    const Community = require('./models/Community');
    const User = require('./models/User');

    const dbStatus = mongoose.connection.readyState === 1 ? 'Connected' : 'Disconnected';

    let communityCount = 0;
    let userCount = 0;
    let hasSuperAdmin = false;

    try {
        [communityCount, userCount, hasSuperAdmin] = await Promise.all([
            Community.countDocuments({ isDeleted: false }),
            User.countDocuments({ isActive: true }),
            User.exists({ role: 'SuperAdmin' })
        ]);
    } catch (e) {
        // Silent — don't fail health check on stats error
    }

    res.json({
        success: true,
        status: 'online',
        timestamp: new Date().toISOString(),
        database: {
            provider: 'MongoDB',
            status: dbStatus,
            host: mongoose.connection.host || 'unknown',
            name: mongoose.connection.name || 'MyAIApp'
        },
        smtp: {
            configured: Boolean(process.env.SMTP_USER && process.env.SMTP_PASS),
            host: process.env.SMTP_HOST || 'not configured'
        },
        ai: {
            provider: 'Google Gemini',
            configured: Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.length > 10),
            model: process.env.GEMINI_MODEL || 'gemini-1.5-flash-latest'
        },
        yolo: {
            configured: require('fs').existsSync(require('path').join(__dirname, 'yolo', 'detect.py'))
        },
        stats: {
            communities: communityCount,
            activeUsers: userCount,
            superAdminSeeded: Boolean(hasSuperAdmin)
        }
    });
});

// ─────────────────────────────────────────────
// 7. 404 Catch-All
// ─────────────────────────────────────────────
app.use((req, res) => {
    console.log(`❓ Unknown route: ${req.method} ${req.originalUrl}`);
    res.status(404).json({
        success: false,
        message: `Route not found: ${req.method} ${req.originalUrl}`
    });
});

// ─────────────────────────────────────────────
// 8. Global Error Handler
// ─────────────────────────────────────────────
app.use((err, req, res, next) => {
    console.error('❌ Global error:', err.message);
    if (err.stack && process.env.NODE_ENV === 'development') {
        console.error(err.stack);
    }

    // Handle Mongoose validation errors
    if (err.name === 'ValidationError') {
        const messages = Object.values(err.errors).map(e => e.message);
        return res.status(400).json({
            success: false,
            message: messages.join(', ')
        });
    }

    // Handle Mongoose duplicate key errors
    if (err.code === 11000) {
        const field = Object.keys(err.keyPattern || {})[0] || 'field';
        return res.status(400).json({
            success: false,
            message: `Duplicate value for ${field}`
        });
    }

    // Handle invalid ObjectId errors
    if (err.name === 'CastError') {
        return res.status(400).json({
            success: false,
            message: `Invalid ${err.path}: ${err.value}`
        });
    }

    res.status(err.status || 500).json({
        success: false,
        message: err.message || 'Internal Server Error'
    });
});

// ─────────────────────────────────────────────
// 9. Start Server
// ─────────────────────────────────────────────
const server = app.listen(PORT, () => {
    console.log(`\n🚀 Community AI Backend is running on port ${PORT}`);
    console.log(`   → Local:            http://localhost:${PORT}`);
    console.log(`   → Android Emulator: http://10.0.2.2:${PORT}`);
    console.log(`   → Physical Device:  http://<YOUR_PC_IP>:${PORT}`);
    console.log(`\n📚 Mounted routes:`);
    console.log(`   • /api/auth           → register, login, OTP, password reset`);
    console.log(`   • /api/users          → profile, change-password, maintainers`);
    console.log(`   • /api/communities    → list, create, update, toggle, delete`);
    console.log(`   • /api/problems       → CRUD, likes, comments, assign, verify, feedback`);
    console.log(`   • /api/notifications  → per-user notification feed`);
    console.log(`   • /api/messages       → community group chat`);
    console.log(`   • /api/chat           → Gemini AI chatbot`);
    console.log(`   • /api/admin          → dashboard, user CRUD, maintainer stats, overrides`);
    console.log(`   • /api/health         → health check\n`);
});

// ─────────────────────────────────────────────
// 10. Graceful Shutdown
// ─────────────────────────────────────────────
function gracefulShutdown(signal) {
    console.log(`\n⚠️ ${signal} received. Shutting down gracefully...`);
    server.close(() => {
        console.log('✅ HTTP server closed');
        mongoose.connection.close(false, () => {
            console.log('✅ MongoDB connection closed');
            process.exit(0);
        });
    });

    // Force close after 10s
    setTimeout(() => {
        console.error('❌ Forcing shutdown after timeout');
        process.exit(1);
    }, 10000);
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// Handle unhandled promise rejections
process.on('unhandledRejection', (err) => {
    console.error('❌ Unhandled rejection:', err.message);
});

// Handle uncaught exceptions
process.on('uncaughtException', (err) => {
    console.error('❌ Uncaught exception:', err.message);
    gracefulShutdown('uncaughtException');
});
const nodemailer = require('nodemailer');

// ─────────────────────────────────────────────
// Safe require for Config model (may not exist yet)
// Falls back to .env if model is unavailable
// ─────────────────────────────────────────────
let Config = null;
try {
    Config = require('../models/Config');
} catch (err) {
    console.log('ℹ️  [EMAIL] Config model not found — using .env SMTP settings only');
}

// ─────────────────────────────────────────────
// Cache transporter so we don't reconnect on every email
// ─────────────────────────────────────────────
let cachedTransporter = null;
let cachedConfigKey = null;

// ─────────────────────────────────────────────
// Build a "config signature" so we know when to rebuild the transporter
// ─────────────────────────────────────────────
const getConfigSignature = (cfg) => `${cfg.host}:${cfg.port}:${cfg.auth.user}`;

// ─────────────────────────────────────────────
// Load SMTP config — prefers DB, falls back to .env
// ─────────────────────────────────────────────
const loadSmtpConfig = async () => {
    let smtpConfig = null;

    // 1. Try DB (Admin Dashboard) — only if Config model exists
    if (Config) {
        try {
            const dbConfig = await Config.findOne({ key: 'SMTP_CONFIG' });
            if (
                dbConfig &&
                dbConfig.value &&
                typeof dbConfig.value === 'object' &&
                dbConfig.value.host &&
                dbConfig.value.user &&
                dbConfig.value.pass
            ) {
                console.log('📧 SMTP config loaded from Database');
                const port = parseInt(dbConfig.value.port) || 587;
                smtpConfig = {
                    host: dbConfig.value.host,
                    port,
                    secure: port === 465,
                    auth: {
                        user: dbConfig.value.user,
                        pass: dbConfig.value.pass
                    }
                };
            }
        } catch (dbError) {
            console.log('⚠️ Could not load SMTP from DB, using .env:', dbError.message);
        }
    }

    // 2. Fallback to .env
    if (!smtpConfig) {
        if (!process.env.SMTP_HOST || !process.env.SMTP_USER || !process.env.SMTP_PASS) {
            throw new Error(
                'SMTP is not configured. Please set SMTP_HOST, SMTP_USER, SMTP_PASS in .env'
            );
        }
        console.log('📧 SMTP config loaded from .env');
        const port = parseInt(process.env.SMTP_PORT) || 587;
        smtpConfig = {
            host: process.env.SMTP_HOST,
            port,
            secure: port === 465, // true for 465 (SSL), false for 587 (STARTTLS)
            auth: {
                user: process.env.SMTP_USER,
                pass: process.env.SMTP_PASS
            }
        };
    }

    return smtpConfig;
};

// ─────────────────────────────────────────────
// Get (or rebuild) the transporter
// ─────────────────────────────────────────────
const getTransporter = async () => {
    const smtpConfig = await loadSmtpConfig();
    const signature = getConfigSignature(smtpConfig);

    // Rebuild if config changed (admin updated via dashboard)
    if (cachedTransporter && cachedConfigKey === signature) {
        return { transporter: cachedTransporter, config: smtpConfig };
    }

    const transporter = nodemailer.createTransport({
        ...smtpConfig,
        pool: true,               // Reuse SMTP connections
        maxConnections: 3,        // Limit concurrency
        maxMessages: 100,         // Recycle after 100 emails
        connectionTimeout: 10000, // 10s to establish TCP
        greetingTimeout: 10000,   // 10s for server greeting
        socketTimeout: 20000      // 20s for socket inactivity
    });

    // Verify once (only on new transporter)
    try {
        await transporter.verify();
        console.log('✅ SMTP transporter verified successfully');
    } catch (verifyError) {
        console.error('❌ SMTP verification failed:', verifyError.message);
        // Clear cache so a retry can rebuild
        cachedTransporter = null;
        cachedConfigKey = null;
        throw new Error(`SMTP authentication failed: ${verifyError.message}`);
    }

    cachedTransporter = transporter;
    cachedConfigKey = signature;
    return { transporter, config: smtpConfig };
};

// ─────────────────────────────────────────────
// Main send function
// ─────────────────────────────────────────────
const sendEmail = async (options) => {
    try {
        const { transporter, config } = await getTransporter();

        const fromName = process.env.EMAIL_FROM_NAME || 'Community AI';
        const fromAddress = process.env.EMAIL_FROM || config.auth.user;

        const mailOptions = {
            from: `"${fromName}" <${fromAddress}>`,
            to: options.email,
            subject: options.subject,
            text: options.message,
            html: options.html || `<p>${options.message}</p>`
        };

        console.log(`📤 Sending email to ${options.email}...`);
        const info = await transporter.sendMail(mailOptions);
        console.log(`✅ Email delivered to ${options.email} [ID: ${info.messageId}]`);
        return info;

    } catch (error) {
        console.error('❌ Email send failed:', error.message);
        // Clear cached transporter on failure so next attempt rebuilds
        cachedTransporter = null;
        cachedConfigKey = null;
        throw new Error(error.message);
    }
};

// ─────────────────────────────────────────────
// Utility: reset cache when admin updates config
// ─────────────────────────────────────────────
const resetTransporterCache = () => {
    cachedTransporter = null;
    cachedConfigKey = null;
    console.log('🔄 SMTP transporter cache cleared');
};

module.exports = sendEmail;
module.exports.resetTransporterCache = resetTransporterCache;
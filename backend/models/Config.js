const mongoose = require('mongoose');

// ─────────────────────────────────────────────
// Allowed config keys (prevents typos)
// ─────────────────────────────────────────────
const ALLOWED_KEYS = [
    'SMTP_CONFIG',
    'GOOGLE_MAPS_API',
    'OPENAI_API_KEY',
    'GEMINI_CONFIG',       // ← NEW
    'YOLO_CONFIG',         // ← NEW
    'FIREBASE_CONFIG',
    'APP_SETTINGS'
];

// ─────────────────────────────────────────────
// Keys that contain secrets (should be masked in API responses)
// ─────────────────────────────────────────────
const SECRET_KEYS = [
    'SMTP_CONFIG',
    'GOOGLE_MAPS_API',
    'OPENAI_API_KEY',
    'GEMINI_CONFIG',
    'FIREBASE_CONFIG'
];

// ─────────────────────────────────────────────
// Change history sub-schema (audit trail)
// ─────────────────────────────────────────────
const changeHistorySchema = new mongoose.Schema(
    {
        changedBy: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        changedByName: { type: String, default: 'System' },
        changedAt: { type: Date, default: Date.now },
        // Store a redacted snapshot — never store raw secrets
        previousValueSummary: { type: String, default: '' },
        note: { type: String, default: '', trim: true, maxlength: 300 }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Config schema
// ─────────────────────────────────────────────
const configSchema = new mongoose.Schema(
    {
        key: {
            type: String,
            required: [true, 'Config key is required'],
            unique: true,
            trim: true,
            uppercase: true,
            enum: {
                values: ALLOWED_KEYS,
                message: `Config key must be one of: ${ALLOWED_KEYS.join(', ')}`
            },
            index: true
        },
        value: {
            type: mongoose.Schema.Types.Mixed,
            required: [true, 'Config value is required'],
            validate: {
                validator: function (v) {
                    // For SMTP_CONFIG specifically, enforce required fields
                    if (this.key === 'SMTP_CONFIG') {
                        if (!v || typeof v !== 'object') return false;
                        return Boolean(v.host && v.port && v.user && v.pass);
                    }
                    // For GEMINI_CONFIG, require an apiKey
                    if (this.key === 'GEMINI_CONFIG') {
                        if (!v || typeof v !== 'object') return false;
                        return Boolean(v.apiKey && v.apiKey.length > 10);
                    }
                    // For YOLO_CONFIG, require a pythonPath or detectScript
                    if (this.key === 'YOLO_CONFIG') {
                        if (!v || typeof v !== 'object') return false;
                        return Boolean(v.pythonPath || v.detectScript);
                    }
                    // For other configs, just require non-null
                    return v !== null && v !== undefined;
                },
                message: function (props) {
                    if (props.path === 'value' && this.key === 'SMTP_CONFIG') {
                        return 'SMTP_CONFIG must include host, port, user, and pass';
                    }
                    if (props.path === 'value' && this.key === 'GEMINI_CONFIG') {
                        return 'GEMINI_CONFIG must include a valid apiKey (length > 10)';
                    }
                    return 'Config value cannot be null or invalid';
                }
            }
        },
        description: {
            type: String,
            default: '',
            trim: true,
            maxlength: 200
        },
        isSecret: {
            type: Boolean,
            default: false,
            index: true
        },
        // Whether the value is currently encrypted at rest
        encrypted: {
            type: Boolean,
            default: false
        },
        updatedBy: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        updatedByName: {
            type: String,
            default: 'System'
        },
        // Audit trail (last 20 changes)
        changeHistory: {
            type: [changeHistorySchema],
            default: []
        }
    },
    {
        timestamps: true
    }
);

// ─────────────────────────────────────────────
// Pre-save hook — set isSecret based on key
// ─────────────────────────────────────────────
configSchema.pre('save', function (next) {
    if (SECRET_KEYS.includes(this.key)) {
        this.isSecret = true;
    }
    next();
});

// ─────────────────────────────────────────────
// Static helpers — DRY lookups
// ─────────────────────────────────────────────

/**
 * Get a config value by key.
 * Returns the value directly, or null if not found.
 */
configSchema.statics.getValue = async function (key) {
    const doc = await this.findOne({ key: key.toUpperCase() });
    return doc ? doc.value : null;
};

/**
 * Set (upsert) a config value.
 * Returns the updated document.
 */
configSchema.statics.setValue = async function (
    key,
    value,
    updatedBy = null,
    updatedByName = 'System',
    description = '',
    note = ''
) {
    const upperKey = key.toUpperCase();

    // Fetch existing doc to capture previous value summary
    const existing = await this.findOne({ key: upperKey });

    // Build a redacted summary of the previous value (never store raw secrets)
    let previousValueSummary = '';
    if (existing) {
        const prev = existing.value;
        if (prev && typeof prev === 'object') {
            const keys = Object.keys(prev);
            previousValueSummary = `Updated keys: ${keys.join(', ')}`;
        } else {
            previousValueSummary = 'Primitive value updated';
        }
    }

    // Append to change history (keep last 20)
    const historyEntry = {
        changedBy: updatedBy,
        changedByName: updatedByName,
        changedAt: new Date(),
        previousValueSummary,
        note
    };

    const update = {
        value,
        updatedBy,
        updatedByName,
        $push: {
            changeHistory: {
                $each: [historyEntry],
                $slice: -20 // keep last 20 entries
            }
        }
    };

    if (description) update.description = description;
    if (SECRET_KEYS.includes(upperKey)) update.isSecret = true;

    return this.findOneAndUpdate(
        { key: upperKey },
        update,
        {
            new: true,
            upsert: true,
            runValidators: true,
            setDefaultsOnInsert: true
        }
    );
};

/**
 * Get SMTP config specifically (raw).
 */
configSchema.statics.getSmtpConfig = async function () {
    return this.getValue('SMTP_CONFIG');
};

/**
 * Get SMTP config in nodemailer-ready format.
 */
configSchema.statics.getSmtpConfigForEmail = async function () {
    const smtp = await this.getValue('SMTP_CONFIG');
    if (!smtp || !smtp.host || !smtp.user || !smtp.pass) return null;

    const port = parseInt(smtp.port) || 587;
    return {
        host: smtp.host,
        port,
        secure: port === 465,
        auth: {
            user: smtp.user,
            pass: smtp.pass
        }
    };
};

/**
 * Get GEMINI config in geminiService-ready format.
 */
configSchema.statics.getGeminiConfig = async function () {
    const cfg = await this.getValue('GEMINI_CONFIG');
    if (!cfg || !cfg.apiKey) return null;
    return {
        apiKey: cfg.apiKey,
        model: cfg.model || 'gemini-1.5-flash-latest'
    };
};

/**
 * Get YOLO config in yoloService-ready format.
 */
configSchema.statics.getYoloConfig = async function () {
    const cfg = await this.getValue('YOLO_CONFIG');
    if (!cfg) return null;
    return {
        pythonPath: cfg.pythonPath || null,
        detectScript: cfg.detectScript || null,
        confThreshold: cfg.confThreshold || 0.35,
        iouThreshold: cfg.iouThreshold || 0.45,
        timeoutMs: cfg.timeoutMs || 60000
    };
};

/**
 * Get a config value with secrets masked (for admin dashboard).
 */
configSchema.statics.getMaskedValue = async function (key) {
    const doc = await this.findOne({ key: key.toUpperCase() });
    if (!doc) return null;

    const obj = doc.toObject();
    const value = { ...obj.value };

    // Mask secret fields
    if (obj.key === 'SMTP_CONFIG') {
        value.pass = value.pass ? '••••••••' : '';
    } else if (obj.key === 'GEMINI_CONFIG') {
        value.apiKey = value.apiKey ? `••••••${value.apiKey.slice(-4)}` : '';
    } else if (obj.key === 'OPENAI_API_KEY' && typeof value === 'string') {
        return `••••••${value.slice(-4)}`;
    }

    return value;
};

/**
 * Get all config keys with secrets masked.
 */
configSchema.statics.getAllMasked = async function () {
    const docs = await this.find({}).sort({ key: 1 });
    return docs.map(doc => {
        const obj = doc.toObject();
        const value = { ...obj.value };

        if (obj.key === 'SMTP_CONFIG') {
            value.pass = value.pass ? '••••••••' : '';
        } else if (obj.key === 'GEMINI_CONFIG') {
            value.apiKey = value.apiKey ? `••••••${value.apiKey.slice(-4)}` : '';
        }

        return {
            key: obj.key,
            value,
            description: obj.description,
            isSecret: obj.isSecret,
            updatedByName: obj.updatedByName,
            updatedAt: obj.updatedAt
        };
    });
};

// ─────────────────────────────────────────────
// toPublicJSON — mask secrets automatically
// ─────────────────────────────────────────────
configSchema.methods.toPublicJSON = function () {
    const obj = this.toObject();
    const value = { ...obj.value };

    if (obj.key === 'SMTP_CONFIG') {
        value.pass = value.pass ? '••••••••' : '';
    } else if (obj.key === 'GEMINI_CONFIG') {
        value.apiKey = value.apiKey ? `••••••${value.apiKey.slice(-4)}` : '';
    }

    return {
        id: obj._id,
        key: obj.key,
        value,
        description: obj.description,
        isSecret: obj.isSecret,
        updatedByName: obj.updatedByName,
        updatedAt: obj.updatedAt,
        createdAt: obj.createdAt
    };
};

// ─────────────────────────────────────────────
// Indexes
// ─────────────────────────────────────────────
configSchema.index({ key: 1 }, { unique: true });
configSchema.index({ isSecret: 1 });
configSchema.index({ updatedAt: -1 });

module.exports = mongoose.model('Config', configSchema);
module.exports.ALLOWED_KEYS = ALLOWED_KEYS;
module.exports.SECRET_KEYS = SECRET_KEYS;
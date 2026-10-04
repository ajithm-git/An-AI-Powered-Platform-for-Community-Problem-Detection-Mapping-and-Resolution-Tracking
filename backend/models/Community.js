const mongoose = require('mongoose');

// ─────────────────────────────────────────────
// Community Settings sub-schema
// ─────────────────────────────────────────────
// Per-community overrides for SMTP, AI, YOLO, etc.
// Falls back to .env or global Config if not set.
// ─────────────────────────────────────────────
const communitySettingsSchema = new mongoose.Schema(
    {
        // ─── SMTP override (for community-specific emails) ───
        smtp: {
            host: { type: String, default: '' },
            port: { type: Number, default: 0 },
            user: { type: String, default: '' },
            pass: { type: String, default: '' },
            fromName: { type: String, default: '' },
            fromEmail: { type: String, default: '' }
        },

        // ─── AI toggles ────────────────────────
        ai: {
            enableGemini: { type: Boolean, default: true },
            enableYolo: { type: Boolean, default: true },
            autoAssignProblems: { type: Boolean, default: true },
            autoNotifyResidentOnResolve: { type: Boolean, default: true },
            yoloConfidenceThreshold: { type: Number, default: 0.35, min: 0, max: 1 },
            geminiModel: { type: String, default: 'gemini-1.5-flash-latest' }
        },

        // ─── Feature toggles ────────────────────
        features: {
            allowResidentFeedback: { type: Boolean, default: true },
            allowLikesAndComments: { type: Boolean, default: true },
            allowGroupChat: { type: Boolean, default: true },
            allowPersonalChat: { type: Boolean, default: true }
        },

        // ─── Notification preferences ───────────
        notifications: {
            emailOnNewProblem: { type: Boolean, default: true },
            emailOnResolve: { type: Boolean, default: true },
            emailOnAssign: { type: Boolean, default: true },
            adminAlertEmail: { type: String, default: '' }
        }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Community Stats sub-schema
// ─────────────────────────────────────────────
const communityStatsSchema = new mongoose.Schema(
    {
        avgResolutionHours: { type: Number, default: 0 },
        totalResolved: { type: Number, default: 0 },
        totalRejected: { type: Number, default: 0 },
        yoloAccuracy: { type: Number, default: 0, min: 0, max: 100 },
        avgResidentRating: { type: Number, default: 0, min: 0, max: 5 },
        lastCalculatedAt: { type: Date, default: null }
    },
    { _id: false }
);

// ─────────────────────────────────────────────
// Community / Organization schema
// ─────────────────────────────────────────────
// Represents an isolated "world" for users, problems,
// chat rooms, and notifications. Examples:
//   - NIE North Campus
//   - NIE South Campus
//   - Green Valley Apartments
// ─────────────────────────────────────────────
const communitySchema = new mongoose.Schema(
    {
        // ─── Identity ───────────────────────────
        name: {
            type: String,
            required: [true, 'Community name is required'],
            trim: true,
            unique: true,
            minlength: 2,
            maxlength: 80
        },

        // URL/room-safe slug, e.g. "nie-north-campus"
        // Auto-generated from name if not supplied
        slug: {
            type: String,
            required: true,
            unique: true,
            lowercase: true,
            trim: true,
            index: true
        },

        // Short description shown to users in the register dropdown
        description: {
            type: String,
            default: '',
            trim: true,
            maxlength: 300
        },

        // ─── Location ───────────────────────────
        address: {
            type: String,
            default: '',
            trim: true,
            maxlength: 200
        },

        // Optional geo coordinates (for future mapping)
        location: {
            latitude: { type: Number, default: null },
            longitude: { type: Number, default: null }
        },

        // Optional: contact email for this community
        contactEmail: {
            type: String,
            default: '',
            lowercase: true,
            trim: true
        },

        // Optional: contact phone
        contactPhone: {
            type: String,
            default: '',
            trim: true
        },

        // ─── Branding ───────────────────────────
        // Logo URL for community header
        logoUrl: {
            type: String,
            default: ''
        },

        // Accent color (brand color per community)
        color: {
            type: String,
            default: '#10B981',
            match: [/^#[0-9A-Fa-f]{6}$/, 'Color must be a hex value like #10B981']
        },

        // ─── Ownership ──────────────────────────
        // Who created this community (admin/super-admin user)
        createdBy: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'User',
            default: null
        },
        createdByName: { type: String, default: '' },

        // ─── Status ─────────────────────────────
        // Active flag — inactive communities won't appear in
        // the register dropdown, but existing users keep their data
        isActive: {
            type: Boolean,
            default: true,
            index: true
        },

        // Soft-delete marker (safer than true delete)
        isDeleted: {
            type: Boolean,
            default: false
        },

        // ─── Denormalized counts (for fast dashboard loads) ───
        memberCount: {
            type: Number,
            default: 0,
            min: 0
        },
        problemCount: {
            type: Number,
            default: 0,
            min: 0
        },
        resolvedCount: {
            type: Number,
            default: 0,
            min: 0
        },

        // ─── Per-community settings ─────────────
        settings: {
            type: communitySettingsSchema,
            default: () => ({})
        },

        // ─── Aggregate stats ────────────────────
        stats: {
            type: communityStatsSchema,
            default: () => ({})
        }
    },
    { timestamps: true }
);

// ─────────────────────────────────────────────
// Auto-generate slug from name if not provided
// ─────────────────────────────────────────────
communitySchema.pre('validate', function (next) {
    // Only set slug on first save or if name is changed and slug is empty
    if (!this.slug && this.name) {
        this.slug = slugify(this.name);
    }
    next();
});

// ─────────────────────────────────────────────
// Helper: slugify
// ─────────────────────────────────────────────
function slugify(str) {
    return str
        .toString()
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9\s-]/g, '')   // remove non-alphanumeric
        .replace(/\s+/g, '-')            // spaces → dashes
        .replace(/-+/g, '-')             // collapse dashes
        .replace(/^-|-$/g, '');          // trim dashes
}

// ─────────────────────────────────────────────
// Public JSON (for dropdown + community header)
// ─────────────────────────────────────────────
communitySchema.methods.toPublicJSON = function () {
    const obj = this.toObject();
    return {
        id: obj._id,
        name: obj.name,
        slug: obj.slug,
        description: obj.description,
        address: obj.address,
        location: obj.location,
        contactEmail: obj.contactEmail,
        contactPhone: obj.contactPhone,
        logoUrl: obj.logoUrl,
        color: obj.color,
        isActive: obj.isActive,
        memberCount: obj.memberCount || 0,
        problemCount: obj.problemCount || 0,
        resolvedCount: obj.resolvedCount || 0,
        stats: obj.stats || {},
        createdByName: obj.createdByName,
        createdAt: obj.createdAt
    };
};

// ─────────────────────────────────────────────
// Full JSON (for Admin Dashboard — includes settings)
// ─────────────────────────────────────────────
communitySchema.methods.toAdminJSON = function () {
    const obj = this.toObject();
    // Mask SMTP password
    const smtp = obj.settings?.smtp || {};
    const maskedSmtp = {
        ...smtp,
        pass: smtp.pass ? '••••••••' : ''
    };

    return {
        ...this.toPublicJSON(),
        settings: {
            ...obj.settings,
            smtp: maskedSmtp
        },
        isDeleted: obj.isDeleted,
        createdBy: obj.createdBy,
        updatedAt: obj.updatedAt
    };
};

// ─────────────────────────────────────────────
// Static helper: generate unique slug
// ─────────────────────────────────────────────
communitySchema.statics.generateUniqueSlug = async function (name) {
    let baseSlug = slugify(name);
    let slug = baseSlug;
    let counter = 1;

    while (await this.exists({ slug })) {
        slug = `${baseSlug}-${counter}`;
        counter++;
        if (counter > 100) {
            // Safety: add timestamp to avoid infinite loop
            slug = `${baseSlug}-${Date.now()}`;
            break;
        }
    }
    return slug;
};

// ─────────────────────────────────────────────
// Static helper: get active communities for register dropdown
// ─────────────────────────────────────────────
communitySchema.statics.getActiveForDropdown = function () {
    return this.find(
        { isActive: true, isDeleted: false },
        { name: 1, slug: 1, description: 1, color: 1, address: 1 }
    ).sort({ name: 1 });
};

// ─────────────────────────────────────────────
// Indexes
// ─────────────────────────────────────────────
communitySchema.index({ name: 1 });
communitySchema.index({ isActive: 1, isDeleted: 1 });
communitySchema.index({ createdAt: -1 });
communitySchema.index({ memberCount: -1 });

module.exports = mongoose.model('Community', communitySchema);
module.exports.slugify = slugify;
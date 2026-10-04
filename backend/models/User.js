const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const userSchema = new mongoose.Schema(
    {
        name: {
            type: String,
            required: [true, 'Please add a name'],
            trim: true
        },
        email: {
            type: String,
            required: [true, 'Please add an email'],
            unique: true,
            lowercase: true,
            trim: true,
            index: true,
            match: [
                /^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$/,
                'Please add a valid email'
            ]
        },
        phone: {
            type: String,
            required: [true, 'Please add a phone number'],
            trim: true,
            match: [
                /^[0-9+\-\s()]{7,20}$/,
                'Please add a valid phone number'
            ]
        },
        password: {
            type: String,
            required: [true, 'Please add a password'],
            minlength: 6,
            select: false // Never returned in queries by default
        },

        // ─────────────────────────────────────────
        // Role — SuperAdmin has no community; others do
        // ─────────────────────────────────────────
        role: {
            type: String,
            enum: ['SuperAdmin', 'Admin', 'Staff', 'Resident'],
            default: 'Resident'
        },

        // ─────────────────────────────────────────
        // Community membership
        // null for SuperAdmin (global access)
        // ─────────────────────────────────────────
        communityId: {
            type: mongoose.Schema.Types.ObjectId,
            ref: 'Community',
            default: null,
            index: true
        },
        communityName: {
            type: String,
            default: '',
            trim: true
        },

        avatarUrl: {
            type: String,
            default: ''
        },
        isVerified: {
            type: Boolean,
            default: false
        },
        isActive: {
            type: Boolean,
            default: true
        },

        // OTP for initial Email Verification
        otp: {
            type: String,
            default: null
        },
        otpExpiry: {
            type: Date,
            default: null
        },

        // OTP for Forgot Password flow
        resetPasswordOtp: {
            type: String,
            default: null
        },
        resetPasswordExpiry: {
            type: Date,
            default: null
        }
    },
    {
        timestamps: true // Adds createdAt and updatedAt
    }
);

// ─────────────────────────────────────────────
// Hash password before saving to database
// ─────────────────────────────────────────────
userSchema.pre('save', async function (next) {
    // Only hash the password if it has been modified (or is new)
    if (!this.isModified('password')) {
        return next();
    }

    try {
        const salt = await bcrypt.genSalt(10);
        this.password = await bcrypt.hash(this.password, salt);
        next();
    } catch (error) {
        next(error);
    }
});

// ─────────────────────────────────────────────
// Method to compare entered password with hashed password in DB
// ─────────────────────────────────────────────
userSchema.methods.matchPassword = async function (enteredPassword) {
    return await bcrypt.compare(enteredPassword, this.password);
};

// ─────────────────────────────────────────────
// Never expose the password field in JSON responses
// ─────────────────────────────────────────────
userSchema.methods.toJSON = function () {
    const obj = this.toObject();
    delete obj.password;
    delete obj.otp;
    delete obj.resetPasswordOtp;
    return obj;
};

// ─────────────────────────────────────────────
// Helper: role checks
// ─────────────────────────────────────────────
userSchema.methods.isSuperAdmin = function () {
    return this.role === 'SuperAdmin';
};

userSchema.methods.isAdminOrAbove = function () {
    return this.role === 'SuperAdmin' || this.role === 'Admin';
};

userSchema.methods.isStaffOrAbove = function () {
    return ['SuperAdmin', 'Admin', 'Staff'].includes(this.role);
};

// ─────────────────────────────────────────────
// Indexes for fast scoped queries
// ─────────────────────────────────────────────
userSchema.index({ communityId: 1, role: 1 });
userSchema.index({ communityId: 1, createdAt: -1 });

module.exports = mongoose.model('User', userSchema);
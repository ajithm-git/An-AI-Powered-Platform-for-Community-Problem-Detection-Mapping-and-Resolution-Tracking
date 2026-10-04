const User = require('../models/User');
const Community = require('../models/Community');
const Notification = require('../models/Notification');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const sendEmail = require('../utils/emailService');

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────
const generateOTP = () => Math.floor(100000 + Math.random() * 900000).toString();
const isValidEmail = (email) => /^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/.test(email);

// Public registration can only pick these roles.
// SuperAdmin is seeded via .env and cannot self-register.
const PUBLIC_ROLES = ['Resident', 'Staff', 'Admin'];
const isValidPublicRole = (role) => PUBLIC_ROLES.includes(role);

// ─────────────────────────────────────────────
// @desc    Register new user & send OTP
// @route   POST /api/auth/register
// @access  Public
// Body: { name, email, phone, password, role, communityId }
// ─────────────────────────────────────────────
exports.register = async (req, res) => {
    try {
        const { name, email, phone, password, role, communityId } = req.body;

        console.log(`\n📝 [REGISTER] Request: ${email} as ${role} (community=${communityId})`);

        // 1. Basic validation
        if (!name || !email || !phone || !password) {
            return res.status(400).json({ success: false, message: 'Please fill all required fields' });
        }
        if (!isValidEmail(email)) {
            return res.status(400).json({ success: false, message: 'Please provide a valid email address' });
        }
        if (password.length < 6) {
            return res.status(400).json({ success: false, message: 'Password must be at least 6 characters' });
        }
        if (role && !isValidPublicRole(role)) {
            return res.status(400).json({ success: false, message: 'Invalid role selected' });
        }
        if (!communityId) {
            return res.status(400).json({ success: false, message: 'Please select your community' });
        }

        const cleanEmail = email.trim().toLowerCase();
        const cleanRole = role || 'Resident';

        // 2. Validate community exists and is active
        const community = await Community.findById(communityId);
        if (!community || community.isDeleted) {
            return res.status(404).json({ success: false, message: 'Selected community not found' });
        }
        if (!community.isActive) {
            return res.status(400).json({
                success: false,
                message: 'Selected community is not accepting new members right now'
            });
        }

        // 3. Check existing user
        const existing = await User.findOne({ email: cleanEmail });

        if (existing) {
            console.log(`⚠️ [REGISTER] Existing user: ${cleanEmail} (verified=${existing.isVerified})`);

            if (!existing.isVerified) {
                // Regenerate OTP and resend + update to the newly chosen community
                const otp = generateOTP();
                existing.otp = otp;
                existing.otpExpiry = Date.now() + 10 * 60 * 1000;
                existing.name = name;
                existing.phone = phone;
                existing.role = cleanRole;
                existing.communityId = community._id;
                existing.communityName = community.name;
                await existing.save();

                try {
                    await sendEmail({
                        email: existing.email,
                        subject: 'Community AI - Secure Verification',
                        message: `Your new verification code is: ${otp}`,
                        html: buildOtpEmail(name, otp, 'Email Verification')
                    });
                    console.log(`✅ [REGISTER] New OTP sent to ${cleanEmail}`);
                    return res.status(200).json({
                        success: true,
                        message: 'A new OTP has been sent to your email.'
                    });
                } catch (emailError) {
                    console.log(`❌ [REGISTER] Email failed: ${emailError.message}`);
                    return res.status(500).json({
                        success: false,
                        message: `Failed to send OTP email: ${emailError.message}`
                    });
                }
            }

            return res.status(400).json({
                success: false,
                message: 'An account with this email already exists. Please login instead.'
            });
        }

        // 4. Generate OTP (Password hashing is handled by the User Model pre-save hook)
        const otp = generateOTP();
        const otpExpiry = Date.now() + 10 * 60 * 1000;

        // 5. Save user (Mongoose will hash password automatically)
        const user = new User({
            name: name.trim(),
            email: cleanEmail,
            phone: phone.trim(),
            password: password, // Plain password passed to model
            role: cleanRole,
            communityId: community._id,
            communityName: community.name,
            otp,
            otpExpiry
        });
        await user.save();
        console.log(`👤 [REGISTER] User saved: ${cleanEmail} → community "${community.name}"`);

        // 6. Send OTP email
        try {
            await sendEmail({
                email: user.email,
                subject: 'Community AI - Secure Verification',
                message: `Your verification code is: ${otp}`,
                html: buildOtpEmail(name, otp, 'Email Verification')
            });
            console.log(`✅ [REGISTER] OTP email sent to ${cleanEmail}`);
            return res.status(201).json({
                success: true,
                message: 'OTP sent to email successfully'
            });
        } catch (emailError) {
            console.log(`❌ [REGISTER] Email failed: ${emailError.message}`);
            await User.findByIdAndDelete(user._id);
            return res.status(500).json({
                success: false,
                message: `Failed to send OTP email: ${emailError.message}`
            });
        }

    } catch (error) {
        if (error.code === 11000) {
            return res.status(400).json({
                success: false,
                message: 'An account with this email already exists. Please login instead.'
            });
        }
        console.error('❌ [REGISTER] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Verify OTP (also creates a welcome notification)
// @route   POST /api/auth/verify-otp
// @access  Public
// ─────────────────────────────────────────────
exports.verifyOtp = async (req, res) => {
    try {
        const { email, otp } = req.body;
        console.log(`\n🔢 [VERIFY-OTP] ${email}`);

        if (!email || !otp) {
            return res.status(400).json({ success: false, message: 'Email and OTP are required' });
        }

        const cleanEmail = email.trim().toLowerCase();
        const cleanOtp = otp.toString().trim();

        const user = await User.findOne({ email: cleanEmail });
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found. Please register first.' });
        }

        if (user.otp !== cleanOtp || !user.otpExpiry || user.otpExpiry < Date.now()) {
            return res.status(400).json({ success: false, message: 'Invalid or expired OTP' });
        }

        user.isVerified = true;
        user.otp = null;
        user.otpExpiry = null;
        await user.save();

        // Send WELCOME notification
        try {
            await Notification.create({
                communityId: user.communityId || null,
                recipientId: user._id,
                recipientEmail: user.email,
                category: 'WELCOME',
                title: 'Welcome to Community AI!',
                body: `Hi ${user.name}, you're now part of ${user.communityName || 'the community'}. Start exploring!`,
                relatedCommunityId: user.communityId || null
            });
        } catch (notifyErr) {
            console.error('⚠️ Welcome notification failed:', notifyErr.message);
        }

        console.log(`✅ [VERIFY-OTP] Verified: ${cleanEmail}`);
        res.status(200).json({
            success: true,
            message: 'Email verified successfully. You can now login.'
        });

    } catch (error) {
        console.error('❌ [VERIFY-OTP] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Login
// @route   POST /api/auth/login
// @access  Public
// Body: { email, password, role }
// ─────────────────────────────────────────────
exports.login = async (req, res) => {
    try {
        const { email, password, role } = req.body;
        console.log(`\n🔐 [LOGIN] ${email} as ${role}`);

        if (!email || !password || !role) {
            return res.status(400).json({ success: false, message: 'Email, password, and role are required' });
        }

        // Accept SuperAdmin login too
        const allRoles = ['SuperAdmin', 'Admin', 'Staff', 'Resident'];
        if (!allRoles.includes(role)) {
            return res.status(400).json({ success: false, message: 'Invalid role selected' });
        }

        const cleanEmail = email.trim().toLowerCase();

        const user = await User.findOne({ email: cleanEmail, role }).select('+password');
        if (!user) {
            console.log(`❌ [LOGIN] No user for ${cleanEmail} as ${role}`);
            return res.status(400).json({
                success: false,
                message: `No ${role} account found with this email`
            });
        }

        if (!user.isVerified) {
            return res.status(400).json({
                success: false,
                message: 'Your email is not verified. Please register again to receive a new OTP.'
            });
        }

        const isMatch = await bcrypt.compare(password, user.password);
        if (!isMatch) {
            return res.status(400).json({ success: false, message: 'Incorrect password' });
        }

        // If user is not SuperAdmin, verify their community still exists + is active
        if (user.role !== 'SuperAdmin') {
            if (!user.communityId) {
                return res.status(403).json({
                    success: false,
                    message: 'Your account is not linked to any community. Please contact support.'
                });
            }

            const community = await Community.findById(user.communityId);
            if (!community || community.isDeleted) {
                return res.status(403).json({
                    success: false,
                    message: 'Your community no longer exists. Please contact support.'
                });
            }
            if (!community.isActive) {
                return res.status(403).json({
                    success: false,
                    message: 'Your community is currently deactivated. Please try again later.'
                });
            }
        }

        const token = jwt.sign(
            {
                id: user._id,
                email: user.email,
                role: user.role,
                communityId: user.communityId || null
            },
            process.env.JWT_SECRET,
            { expiresIn: process.env.JWT_EXPIRE || '30d' }
        );

        console.log(`✅ [LOGIN] Success: ${cleanEmail} (role=${user.role})`);

        res.status(200).json({
            success: true,
            token,
            user: {
                id: user._id,
                name: user.name,
                email: user.email,
                phone: user.phone,
                role: user.role,
                communityId: user.communityId,
                communityName: user.communityName || '',
                avatarUrl: user.avatarUrl || ''
            }
        });

    } catch (error) {
        console.error('❌ [LOGIN] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Forgot Password (Send Reset OTP)
// @route   POST /api/auth/forgot-password
// @access  Public
// ─────────────────────────────────────────────
exports.forgotPassword = async (req, res) => {
    try {
        const { email } = req.body;
        console.log(`\n🔑 [FORGOT] ${email}`);

        if (!email) {
            return res.status(400).json({ success: false, message: 'Email is required' });
        }

        const cleanEmail = email.trim().toLowerCase();
        const user = await User.findOne({ email: cleanEmail });
        if (!user) {
            return res.status(404).json({ success: false, message: 'No user found with this email' });
        }

        const otp = generateOTP();
        user.resetPasswordOtp = otp;
        user.resetPasswordExpiry = Date.now() + 10 * 60 * 1000;
        await user.save();

        try {
            await sendEmail({
                email: user.email,
                subject: 'Community AI - Password Reset',
                message: `Your password reset code is: ${otp}`,
                html: buildOtpEmail(user.name, otp, 'Password Reset')
            });
            console.log(`✅ [FORGOT] Reset OTP sent to ${cleanEmail}`);
            return res.status(200).json({ success: true, message: 'Password reset OTP sent to email' });
        } catch (emailError) {
            console.log(`❌ [FORGOT] Email failed: ${emailError.message}`);
            return res.status(500).json({
                success: false,
                message: `Could not send reset email: ${emailError.message}`
            });
        }

    } catch (error) {
        console.error('❌ [FORGOT] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Reset Password
// @route   POST /api/auth/reset-password
// @access  Public
// ─────────────────────────────────────────────
exports.resetPassword = async (req, res) => {
    try {
        const { email, otp, newPassword } = req.body;
        console.log(`\n🔐 [RESET] ${email}`);

        if (!email || !otp || !newPassword) {
            return res.status(400).json({ success: false, message: 'Email, OTP, and new password are required' });
        }
        if (newPassword.length < 6) {
            return res.status(400).json({ success: false, message: 'Password must be at least 6 characters' });
        }

        const cleanEmail = email.trim().toLowerCase();
        const cleanOtp = otp.toString().trim();

        const user = await User.findOne({ email: cleanEmail });
        if (!user) {
            return res.status(404).json({ success: false, message: 'User not found' });
        }

        if (user.resetPasswordOtp !== cleanOtp || !user.resetPasswordExpiry || user.resetPasswordExpiry < Date.now()) {
            return res.status(400).json({ success: false, message: 'Invalid or expired OTP' });
        }

        // Password hashing handled by the User Model pre-save hook
        user.password = newPassword;
        user.resetPasswordOtp = null;
        user.resetPasswordExpiry = null;
        await user.save();

        console.log(`✅ [RESET] Password reset for ${cleanEmail}`);
        res.status(200).json({ success: true, message: 'Password reset successful. Please login.' });

    } catch (error) {
        console.error('❌ [RESET] Error:', error);
        res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ─────────────────────────────────────────────
// @desc    Seed SuperAdmin (called on server start)
// @access  Internal (not exposed via route)
// ─────────────────────────────────────────────
exports.ensureSuperAdmin = async () => {
    try {
        const email = process.env.SUPERADMIN_EMAIL;
        const password = process.env.SUPERADMIN_PASSWORD;

        if (!email || !password) {
            console.log('ℹ️  [SUPERADMIN] Not configured in .env — skipping seed');
            return;
        }

        const cleanEmail = email.trim().toLowerCase();
        const existing = await User.findOne({ email: cleanEmail, role: 'SuperAdmin' });

        if (existing) {
            console.log(`ℹ️  [SUPERADMIN] Already exists: ${cleanEmail}`);
            return;
        }

        // Password hashing handled by the User Model pre-save hook
        await User.create({
            name: 'Super Admin',
            email: cleanEmail,
            phone: '0000000000',
            password: password, // Plain password passed to model
            role: 'SuperAdmin',
            communityId: null,
            communityName: '',
            isVerified: true
        });

        console.log(`✅ [SUPERADMIN] Created: ${cleanEmail}`);
    } catch (error) {
        console.error('❌ [SUPERADMIN] Seed failed:', error.message);
    }
};

// ─────────────────────────────────────────────
// Email HTML template
// ─────────────────────────────────────────────
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
const mongoose = require('mongoose');
const Problem = require('../models/Problem');
const User = require('../models/User');
const Notification = require('../models/Notification');
const sendEmail = require('../utils/emailService');
const {
    analyzeProblem,
    draftProblemFields,
    generateWorkChecklist,
    isGeminiConfigured
} = require('../utils/geminiService');
const {
    detectObjects,
    verifyResolution: yoloVerifyResolution,
    isYoloConfigured,
    mapClassToCategory,
    computeSeverity
} = require('../utils/yoloService');
const { userInSameCommunity } = require('../middleware/authMiddleware');

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

function scopedProblemFilter(req, extra = {}) {
    const filter = { isDeleted: false, ...extra };

    if (req.user.role === 'SuperAdmin') {
        if (req.query.communityId) {
            filter.communityId = req.query.communityId;
        }
    } else {
        filter.communityId = req.user.communityId;
    }
    return filter;
}

async function createNotification({
    communityId,
    recipientId,
    recipientEmail,
    category,
    title,
    body,
    relatedProblemId = null,
    actorId = null,
    actorName = '',
    actorAvatar = ''
}) {
    try {
        await Notification.create({
            communityId,
            recipientId,
            recipientEmail,
            category,
            title,
            body,
            relatedProblemId,
            actorId,
            actorName,
            actorAvatar
        });
    } catch (err) {
        console.error('⚠️ Notification create failed:', err.message);
    }
}

function isValidObjectId(id) {
    return mongoose.Types.ObjectId.isValid(id);
}

function buildProblemAlertHtml(problem) {
    const yoloLine = (problem.yoloDetections && problem.yoloDetections.length > 0)
        ? `<p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>🎯 YOLO detected:</b> ${
            problem.yoloDetections
                .map(d => `${d.class} (${(d.confidence * 100).toFixed(0)}%)`)
                .join(', ')
        }</p>`
        : '';

    return `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #F8FAFC; border-radius: 16px;">
            <div style="text-align: center;">
                <h1 style="color: #10B981; margin: 0;">Community AI</h1>
                <p style="color: #64748B; font-size: 14px; margin-top: 4px;">New Problem Reported</p>
                <p style="color: #94A3B8; font-size: 12px;">${problem.communityName || ''}</p>
            </div>
            <div style="background: #FFFFFF; padding: 20px; border-radius: 12px; margin-top: 20px;">
                <h2 style="color: #0F172A; margin-top: 0;">${problem.title}</h2>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Category:</b> ${problem.category}</p>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Location:</b> ${problem.location}</p>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Priority:</b> ${problem.priority}</p>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Reporter:</b> ${problem.reporterName} (${problem.reporterRole})</p>
                ${yoloLine}
                <hr style="border: none; border-top: 1px solid #E2E8F0; margin: 16px 0;" />
                <p style="color: #0F172A; font-size: 14px;">${problem.description}</p>
                ${problem.aiDiagnostic && problem.aiDiagnostic.summary ? `
                    <div style="background: #ECFDF5; padding: 12px; border-radius: 10px; margin-top: 16px;">
                        <p style="color: #059669; font-size: 13px; margin: 0;"><b>🤖 AI Diagnostic:</b> ${problem.aiDiagnostic.summary}</p>
                        <p style="color: #059669; font-size: 12px; margin: 8px 0 0 0;"><b>Recommended:</b> ${problem.aiDiagnostic.recommendedAction}</p>
                        <p style="color: #059669; font-size: 12px; margin: 4px 0 0 0;"><b>Severity:</b> ${problem.aiDiagnostic.severity} &nbsp;|&nbsp; <b>ETA:</b> ~${problem.aiDiagnostic.estimatedHours}h</p>
                    </div>
                ` : ''}
            </div>
            <p style="color: #94A3B8; font-size: 11px; text-align: center; margin-top: 16px;">Log in to Community AI to view or assign this problem.</p>
        </div>
    `;
}

// ═════════════════════════════════════════════
// Rich "Fix Complete" email to resident
// ═════════════════════════════════════════════
function buildFixCompleteHtml(problem) {
    const beforeImg = problem.beforeImage && problem.beforeImage.length > 100
        ? `<img src="${problem.beforeImage}" alt="Before" style="width: 100%; max-width: 240px; border-radius: 10px; margin: 4px;" />`
        : '<div style="padding: 40px; background: #F1F5F9; border-radius: 10px; color: #94A3B8; text-align: center; margin: 4px;">No before photo</div>';

    const afterImg = problem.afterImage && problem.afterImage.length > 100
        ? `<img src="${problem.afterImage}" alt="After" style="width: 100%; max-width: 240px; border-radius: 10px; margin: 4px;" />`
        : '<div style="padding: 40px; background: #F1F5F9; border-radius: 10px; color: #94A3B8; text-align: center; margin: 4px;">No after photo</div>';

    const v = problem.resolutionVerification || {};
    const verdictText = v.verdict === 'VERIFIED'
        ? '✅ Verified by AI'
        : v.verdict === 'PARTIAL'
            ? '⚠️ Partially resolved'
            : v.verdict === 'NOT_VERIFIED'
                ? '❌ Still has issues'
                : 'ℹ️ Manually resolved';

    return `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; background: #F8FAFC; border-radius: 16px;">
            <div style="text-align: center;">
                <h1 style="color: #10B981; margin: 0;">Community AI</h1>
                <p style="color: #64748B; font-size: 14px; margin-top: 4px;">Your Report Has Been Fixed</p>
            </div>
            <div style="background: #FFFFFF; padding: 20px; border-radius: 12px; margin-top: 20px;">
                <h2 style="color: #0F172A; margin-top: 0;">${problem.title}</h2>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Location:</b> ${problem.location}</p>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Fixed by:</b> ${problem.assignedToName || 'Maintenance team'}</p>
                <p style="color: #64748B; font-size: 13px; margin: 4px 0;"><b>Verdict:</b> ${verdictText}</p>

                <hr style="border: none; border-top: 1px solid #E2E8F0; margin: 16px 0;" />

                <h3 style="color: #0F172A; margin: 0 0 8px 0; font-size: 15px;">Before &amp; After</h3>
                <table style="width: 100%;">
                    <tr>
                        <td style="vertical-align: top; text-align: center;">
                            <p style="color: #64748B; font-size: 12px; margin: 0 0 4px 0;"><b>BEFORE</b></p>
                            ${beforeImg}
                        </td>
                        <td style="vertical-align: top; text-align: center;">
                            <p style="color: #64748B; font-size: 12px; margin: 0 0 4px 0;"><b>AFTER</b></p>
                            ${afterImg}
                        </td>
                    </tr>
                </table>

                ${v.summary ? `
                    <div style="background: #ECFDF5; padding: 12px; border-radius: 10px; margin-top: 16px;">
                        <p style="color: #059669; font-size: 13px; margin: 0;"><b>AI Verification:</b> ${v.summary}</p>
                    </div>
                ` : ''}

                <div style="text-align: center; margin-top: 20px;">
                    <a href="communityai://problem/${problem._id}" style="display: inline-block; padding: 12px 24px; background: #10B981; color: #FFFFFF; text-decoration: none; border-radius: 8px; font-weight: bold;">View &amp; Rate This Fix</a>
                </div>
                <p style="color: #94A3B8; font-size: 11px; text-align: center; margin-top: 12px;">Please open the app to rate this repair and give feedback.</p>
            </div>
        </div>
    `;
}

async function autoAssignToLeastBusyMaintainer(problem) {
    try {
        const maintainers = await User.find({
            communityId: problem.communityId,
            role: { $in: ['Staff', 'Admin'] },
            isActive: true
        }).select('_id name email role');

        if (maintainers.length === 0) {
            console.log('ℹ️ [AUTO-ASSIGN] No maintainers available in this community');
            return null;
        }

        const loadPromises = maintainers.map(async (m) => {
            const count = await Problem.countDocuments({
                communityId: problem.communityId,
                assignedTo: m._id,
                status: { $in: ['Assigned', 'In Progress'] },
                isDeleted: false
            });
            return { maintainer: m, activeCount: count };
        });

        const loads = await Promise.all(loadPromises);
        loads.sort((a, b) => a.activeCount - b.activeCount);

        const chosen = loads[0];
        console.log(`🎯 [AUTO-ASSIGN] ${chosen.maintainer.name} has ${chosen.activeCount} active tasks — selecting`);

        problem.assignedTo = chosen.maintainer._id;
        problem.assignedToName = chosen.maintainer.name;
        problem.assignedAt = new Date();
        problem.status = 'Assigned';
        problem.autoAssigned = true;
        problem.statusHistory.push({
            status: 'Assigned',
            changedByName: 'System (auto)',
            note: `Auto-assigned to ${chosen.maintainer.name} (fewest active tasks)`
        });
        await problem.save();

        await createNotification({
            communityId: problem.communityId,
            recipientId: chosen.maintainer._id,
            recipientEmail: chosen.maintainer.email,
            category: 'PROBLEM_ASSIGNED',
            title: '🤖 New task auto-assigned',
            body: `You've been assigned: "${problem.title}" at ${problem.location}`,
            relatedProblemId: problem._id,
            actorName: 'System'
        });

        return chosen.maintainer;
    } catch (err) {
        console.error('⚠️ [AUTO-ASSIGN] Failed:', err.message);
        return null;
    }
}

// ═════════════════════════════════════════════
// ANALYZE DRAFT
// ═════════════════════════════════════════════
exports.analyzeProblemDraft = async (req, res) => {
    try {
        const { beforeImage, locationHint } = req.body;

        console.log(`\n🔍 [ANALYZE DRAFT] By: ${req.user.email}`);

        if (!req.user.communityId && req.user.role !== 'SuperAdmin') {
            return res.status(403).json({
                success: false,
                message: 'You must belong to a community to report problems'
            });
        }

        if (!beforeImage || beforeImage.length < 100) {
            return res.status(400).json({
                success: false,
                message: 'A photo is required for analysis'
            });
        }

        let yoloResult = { success: false, detections: [], modelLoaded: '', inferenceMs: 0 };

        if (isYoloConfigured()) {
            try {
                console.log('🎯 Running YOLO detection...');
                yoloResult = await detectObjects(beforeImage);
            } catch (err) {
                console.error('⚠️ YOLO exception:', err.message);
            }
        }

        let draft = null;

        if (isGeminiConfigured()) {
            try {
                console.log('🤖 Asking Gemini to draft problem fields...');
                draft = await draftProblemFields(beforeImage, yoloResult.detections || [], locationHint || '');
            } catch (err) {
                console.error('⚠️ Gemini draft failed:', err.message);
            }
        }

        if (!draft) {
            const topDetection = yoloResult.detections.reduce(
                (best, d) => (d.confidence > (best.confidence || 0) ? d : best),
                {}
            );
            const inferredCategory = topDetection.class ? mapClassToCategory(topDetection.class) : 'Other';

            draft = {
                title: topDetection.class ? `${topDetection.class.replace('_', ' ')} detected` : 'Community problem reported',
                description: topDetection.class ? `YOLO detected ${topDetection.class.replace('_', ' ')}. Please describe in detail.` : 'Please describe the issue.',
                category: inferredCategory,
                priority: topDetection.class === 'flooding' ? 'High' : 'Medium',
                severity: computeSeverity(yoloResult.detections) || 'Medium',
                estimatedHours: 2,
                recommendedAction: 'Inspect on-site and schedule repair.',
                confidenceScore: 50,
                detectedObjects: yoloResult.detections.map(d => d.class)
            };
        }

        return res.status(200).json({
            success: true,
            message: 'Analysis complete',
            yolo: {
                success: yoloResult.success,
                modelLoaded: yoloResult.modelLoaded,
                inferenceMs: yoloResult.inferenceMs,
                detections: yoloResult.detections || []
            },
            draft: {
                title: draft.title || '',
                description: draft.description || '',
                category: draft.category || 'Other',
                priority: draft.priority || 'Medium',
                severity: draft.severity || 'Medium',
                estimatedHours: draft.estimatedHours || 2,
                recommendedAction: draft.recommendedAction || '',
                confidenceScore: draft.confidenceScore || 60,
                detectedObjects: draft.detectedObjects || []
            }
        });

    } catch (error) {
        console.error('❌ [ANALYZE DRAFT] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// CREATE PROBLEM
// ═════════════════════════════════════════════
exports.createProblem = async (req, res) => {
    try {
        const { title, category, location, description, priority, beforeImage } = req.body;

        console.log(`\n📝 [CREATE PROBLEM] By: ${req.user.email}`);

        if (!title || !location || !description) {
            return res.status(400).json({
                success: false,
                message: 'Title, location, and description are required'
            });
        }

        if (!req.user.communityId && req.user.role !== 'SuperAdmin') {
            return res.status(403).json({
                success: false,
                message: 'You must belong to a community to report problems'
            });
        }

        let yoloResult = { success: false, detections: [], modelLoaded: '', inferenceMs: 0 };

        if (beforeImage && isYoloConfigured()) {
            try {
                yoloResult = await detectObjects(beforeImage);
            } catch (err) {
                console.error('⚠️ YOLO exception:', err.message);
            }
        }

        let finalCategory = category || 'Other';
        if ((!category || category === 'Other') && yoloResult.success && yoloResult.detections.length > 0) {
            const topDetection = yoloResult.detections.reduce(
                (best, d) => (d.confidence > (best.confidence || 0) ? d : best),
                {}
            );
            const inferred = mapClassToCategory(topDetection.class);
            if (inferred && inferred !== 'Other') finalCategory = inferred;
        }

        let diagnostic = {
            summary: '',
            recommendedAction: '',
            detectedObjects: yoloResult.detections.map(d => d.class),
            severity: computeSeverity(yoloResult.detections) || 'Medium',
            estimatedHours: 2,
            confidenceScore: 60
        };

        if (isGeminiConfigured()) {
            try {
                diagnostic = await analyzeProblem(beforeImage || '', title, description, location, finalCategory, yoloResult.detections);
            } catch (err) {
                console.error('⚠️ Gemini analyze failed:', err.message);
            }
        }

        const problem = new Problem({
            communityId: req.user.communityId,
            communityName: req.user.communityName || '',
            title: title.trim(),
            category: finalCategory,
            location: location.trim(),
            description: description.trim(),
            priority: priority || 'Medium',
            beforeImage: beforeImage || '',
            reporterId: req.user._id,
            reporterName: req.user.name,
            reporterRole: req.user.role,
            reporterEmail: req.user.email,
            reporterAvatar: req.user.avatarUrl || '',
            yoloDetections: yoloResult.success ? yoloResult.detections : [],
            yoloModelName: yoloResult.modelLoaded || '',
            yoloInferenceMs: yoloResult.inferenceMs || 0,
            aiDiagnostic: diagnostic,
            status: 'Open',
            statusHistory: [{
                status: 'Open',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: 'Problem reported'
            }]
        });

        await problem.save();
        console.log(`✅ Problem created: ${problem._id}`);

        try {
            await autoAssignToLeastBusyMaintainer(problem);
        } catch (err) {
            console.error('⚠️ Auto-assign failed:', err.message);
        }

        const alertEmail = process.env.ADMIN_ALERT_EMAIL;
        if (alertEmail) {
            try {
                await sendEmail({
                    email: alertEmail,
                    subject: `🚨 New Problem [${problem.communityName}]: ${problem.title}`,
                    message: `New problem reported at ${problem.location}`,
                    html: buildProblemAlertHtml(problem)
                });
            } catch (err) { /* silent */ }
        }

        if (!problem.assignedTo) {
            try {
                const staff = await User.find({
                    communityId: problem.communityId,
                    role: { $in: ['Admin', 'Staff'] },
                    isActive: true
                }).select('_id email');

                for (const s of staff) {
                    await createNotification({
                        communityId: problem.communityId,
                        recipientId: s._id,
                        recipientEmail: s.email,
                        category: 'NEW_PROBLEM_NEARBY',
                        title: `New problem: ${problem.title}`,
                        body: `${problem.reporterName} reported "${problem.title}" at ${problem.location}`,
                        relatedProblemId: problem._id,
                        actorId: req.user._id,
                        actorName: req.user.name,
                        actorAvatar: req.user.avatarUrl || ''
                    });
                }
            } catch (err) { /* silent */ }
        }

        const freshProblem = await Problem.findById(problem._id);

        return res.status(201).json({
            success: true,
            message: 'Problem reported successfully',
            problem: freshProblem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [CREATE PROBLEM] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// GET WORK CHECKLIST (AI-generated)
// ═════════════════════════════════════════════
exports.getWorkChecklist = async (req, res) => {
    try {
        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isAdmin = ['Admin', 'SuperAdmin'].includes(req.user.role);
        const isAssigned = problem.assignedTo?.toString() === req.user._id.toString();

        if (!isAdmin && !isAssigned) {
            return res.status(403).json({
                success: false,
                message: 'Only the assigned maintainer or an Admin can access the work checklist'
            });
        }

        // If checklist already exists with steps, return it
        if (problem.workChecklist && problem.workChecklist.steps && problem.workChecklist.steps.length > 0) {
            console.log(`✅ [WORK CHECKLIST] Returning cached checklist for ${problem._id}`);
            return res.status(200).json({
                success: true,
                message: 'Checklist retrieved',
                workChecklist: problem.toPublicJSON().workChecklist,
                fromCache: true
            });
        }

        // Generate new checklist
        console.log(`\n🤖 [WORK CHECKLIST] Generating new checklist for ${problem._id}`);

        if (!isGeminiConfigured()) {
            return res.status(503).json({
                success: false,
                message: 'AI is not configured. Please contact support.'
            });
        }

        const checklist = await generateWorkChecklist(problem, problem.yoloDetections || []);

        problem.workChecklist = {
            steps: checklist.steps.map(s => ({
                stepNumber: s.stepNumber,
                title: s.title,
                description: s.description,
                estimatedMinutes: s.estimatedMinutes,
                safetyNote: s.safetyNote,
                materialsNeeded: s.materialsNeeded,
                isCompleted: false,
                completedAt: null
            })),
            totalEstimateHours: checklist.totalEstimateHours,
            safetyOverview: checklist.safetyOverview,
            generatedAt: checklist.generatedAt,
            completedStepsCount: 0,
            isChecklistCompleted: false
        };

        await problem.save();

        console.log(`✅ [WORK CHECKLIST] Generated ${checklist.steps.length} steps`);

        return res.status(200).json({
            success: true,
            message: 'Checklist generated',
            workChecklist: problem.toPublicJSON().workChecklist,
            fromCache: false
        });

    } catch (error) {
        console.error('❌ [GET WORK CHECKLIST] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// TOGGLE CHECKLIST STEP
// ═════════════════════════════════════════════
exports.toggleChecklistStep = async (req, res) => {
    try {
        const { stepId, isCompleted } = req.body;

        if (!stepId) {
            return res.status(400).json({ success: false, message: 'stepId is required' });
        }

        if (!isValidObjectId(stepId)) {
            return res.status(400).json({ success: false, message: 'Invalid stepId format' });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isAdmin = ['Admin', 'SuperAdmin'].includes(req.user.role);
        const isAssigned = problem.assignedTo?.toString() === req.user._id.toString();

        if (!isAdmin && !isAssigned) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        if (!problem.workChecklist || !problem.workChecklist.steps || problem.workChecklist.steps.length === 0) {
            return res.status(400).json({ success: false, message: 'No work checklist exists for this problem' });
        }

        const step = problem.workChecklist.steps.id(stepId);
        if (!step) {
            return res.status(404).json({ success: false, message: 'Step not found' });
        }

        step.isCompleted = Boolean(isCompleted);
        step.completedAt = isCompleted ? new Date() : null;

        // Recalculate completed steps count
        const completedCount = problem.workChecklist.steps.filter(s => s.isCompleted).length;
        problem.workChecklist.completedStepsCount = completedCount;

        // If all steps completed, mark checklist as complete and set status to In Progress
        const allDone = problem.workChecklist.steps.every(s => s.isCompleted);
        problem.workChecklist.isChecklistCompleted = allDone;

        if (allDone && problem.status === 'Assigned') {
            problem.status = 'In Progress';
            problem.statusHistory.push({
                status: 'In Progress',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: 'All work checklist steps completed. Awaiting final verification photo.'
            });
        }

        await problem.save();

        console.log(`✅ [TOGGLE STEP] ${stepId} → ${isCompleted} on problem ${problem._id}`);

        return res.status(200).json({
            success: true,
            message: 'Step updated',
            workChecklist: problem.toPublicJSON().workChecklist,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [TOGGLE STEP] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// MARK RESIDENT VIEWED (Q4 gate)
// ═════════════════════════════════════════════
exports.markResidentViewed = async (req, res) => {
    try {
        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        if (problem.reporterId.toString() !== req.user._id.toString()) {
            return res.status(403).json({ success: false, message: 'Only the reporter can mark this as viewed' });
        }

        if (problem.status === 'Resolved' && !problem.residentViewedAt) {
            problem.residentViewedAt = new Date();
            await problem.save();
            console.log(`👁️ [RESIDENT VIEWED] ${problem._id} first viewed by ${req.user.email}`);
        }

        return res.status(200).json({
            success: true,
            message: 'Marked as viewed',
            residentViewedAt: problem.residentViewedAt,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [MARK VIEWED] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// VERIFY RESOLUTION
// ═════════════════════════════════════════════
exports.verifyResolution = async (req, res) => {
    try {
        const { afterImage, note } = req.body;

        if (!afterImage || afterImage.length < 100) {
            return res.status(400).json({
                success: false,
                message: 'An after-photo is required for verification'
            });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isAdmin = ['Admin', 'SuperAdmin'].includes(req.user.role);
        const isAssigned = problem.assignedTo?.toString() === req.user._id.toString();

        if (!isAdmin && !isAssigned) {
            return res.status(403).json({
                success: false,
                message: 'Only Admin or the assigned Maintainer can verify resolution'
            });
        }

        console.log(`\n🔍 [VERIFY RESOLUTION] Problem: ${problem._id} by ${req.user.name}`);

        problem.afterImage = afterImage;

        let afterYolo = { success: false, detections: [], modelLoaded: '', inferenceMs: 0 };

        if (isYoloConfigured()) {
            try {
                afterYolo = await detectObjects(afterImage);
                console.log(`✅ After YOLO: ${afterYolo.detections?.length || 0} object(s)`);
            } catch (err) {
                console.error('⚠️ After YOLO failed:', err.message);
            }
        }

        problem.afterYoloDetections = afterYolo.success ? afterYolo.detections : [];

        const verification = yoloVerifyResolution(
            problem.yoloDetections || [],
            afterYolo.detections || []
        );

        console.log(`📊 Verdict: ${verification.verdict} (${verification.confidence}%)`);

        problem.resolutionVerification = {
            verdict: verification.verdict,
            confidence: verification.confidence,
            summary: verification.summary,
            beforeClasses: verification.beforeClasses,
            afterClasses: verification.afterClasses,
            stillPresent: verification.stillPresent,
            newIssues: verification.newIssues,
            resolved: verification.resolved,
            verifiedAt: new Date(),
            verifiedBy: req.user._id
        };

        if (verification.verdict === 'VERIFIED') {
            problem.status = 'Resolved';
            problem.feedbackStatus = 'pending';
            problem.residentViewedAt = null; // Reset — resident hasn't seen this fix yet
            problem.statusHistory.push({
                status: 'Resolved',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: note || `YOLO verified: ${verification.summary}`
            });
        } else {
            problem.status = 'In Progress';
            problem.statusHistory.push({
                status: 'In Progress',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: `YOLO: ${verification.summary}`
            });
        }

        await problem.save();

        // ═════════════════════════════════════════════
        // SEND NOTIFICATIONS (in-app + email if VERIFIED)
        // ═════════════════════════════════════════════
        if (verification.verdict === 'VERIFIED') {
            await createNotification({
                communityId: problem.communityId,
                recipientId: problem.reporterId,
                recipientEmail: problem.reporterEmail,
                category: 'PROBLEM_RESOLVED',
                title: `✅ Your report has been fixed!`,
                body: `"${problem.title}" — tap to see before/after and rate the fix.`,
                relatedProblemId: problem._id,
                actorId: req.user._id,
                actorName: req.user.name,
                actorAvatar: req.user.avatarUrl || ''
            });

            try {
                await sendEmail({
                    email: problem.reporterEmail,
                    subject: `✅ Your report has been fixed: ${problem.title}`,
                    message: `Your report "${problem.title}" has been fixed.`,
                    html: buildFixCompleteHtml(problem)
                });
                problem.residentNotifiedAt = new Date();
                await problem.save();
                console.log(`📧 [FIX COMPLETE] Email sent to ${problem.reporterEmail}`);
            } catch (emailErr) {
                console.error('⚠️ Fix-complete email failed:', emailErr.message);
            }
        } else {
            await createNotification({
                communityId: problem.communityId,
                recipientId: problem.reporterId,
                recipientEmail: problem.reporterEmail,
                category: 'PROBLEM_UPDATED',
                title: `Report status update`,
                body: `"${problem.title}" — ${verification.summary}`,
                relatedProblemId: problem._id,
                actorId: req.user._id,
                actorName: req.user.name,
                actorAvatar: req.user.avatarUrl || ''
            });
        }

        return res.status(200).json({
            success: true,
            message: verification.verdict === 'VERIFIED'
                ? 'Resolution verified! Resident notified.'
                : `Verification: ${verification.verdict}. ${verification.summary}`,
            verification: problem.resolutionVerification,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [VERIFY RESOLUTION] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// SUBMIT FEEDBACK (with Q4 gate)
// ═════════════════════════════════════════════
exports.submitFeedback = async (req, res) => {
    try {
        const { rating, comment } = req.body;

        const ratingNum = parseInt(rating, 10);
        if (!ratingNum || ratingNum < 1 || ratingNum > 5) {
            return res.status(400).json({
                success: false,
                message: 'Rating must be between 1 and 5'
            });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        if (problem.reporterId.toString() !== req.user._id.toString()) {
            return res.status(403).json({
                success: false,
                message: 'Only the reporter can submit feedback'
            });
        }

        if (problem.status !== 'Resolved') {
            return res.status(400).json({
                success: false,
                message: 'Feedback can only be submitted on resolved problems'
            });
        }

        if (!problem.residentViewedAt) {
            return res.status(400).json({
                success: false,
                message: 'Please view the fix before submitting feedback'
            });
        }

        if (problem.feedbackStatus === 'submitted') {
            return res.status(400).json({
                success: false,
                message: 'Feedback already submitted for this problem'
            });
        }

        console.log(`\n⭐ [FEEDBACK] Problem: ${problem._id} — ${ratingNum}/5`);

        problem.feedback = {
            rating: ratingNum,
            comment: comment ? String(comment).trim().slice(0, 500) : '',
            submittedAt: new Date()
        };
        problem.feedbackStatus = 'submitted';
        await problem.save();

        if (problem.assignedTo) {
            const maintainer = await User.findById(problem.assignedTo);
            if (maintainer) {
                await createNotification({
                    communityId: problem.communityId,
                    recipientId: maintainer._id,
                    recipientEmail: maintainer.email,
                    category: 'PROBLEM_UPDATED',
                    title: `⭐ ${ratingNum}-star feedback received`,
                    body: `${req.user.name} rated your work on "${problem.title}"${comment ? `: "${String(comment).slice(0, 80)}"` : ''}`,
                    relatedProblemId: problem._id,
                    actorId: req.user._id,
                    actorName: req.user.name,
                    actorAvatar: req.user.avatarUrl || ''
                });
            }
        }

        return res.status(200).json({
            success: true,
            message: 'Thanks for your feedback!',
            feedback: problem.feedback,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [SUBMIT FEEDBACK] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// GET MAINTAINER'S PROBLEMS (dashboard)
// ═════════════════════════════════════════════
exports.getMaintainerProblems = async (req, res) => {
    try {
        if (!['Staff', 'Admin', 'SuperAdmin'].includes(req.user.role)) {
            return res.status(403).json({
                success: false,
                message: 'Access denied — Staff/Admin only'
            });
        }

        const baseFilter = {
            isDeleted: false,
            communityId: req.user.communityId
        };

        const now = new Date();
        const oneDayAgo = new Date(now.getTime() - 24 * 60 * 60 * 1000);

        const [assignedToMe, highPriority, recent, awaitingFeedback] = await Promise.all([
            Problem.find({
                ...baseFilter,
                assignedTo: req.user._id,
                status: { $in: ['Assigned', 'In Progress', 'Resolved'] }
            }).sort({ assignedAt: -1, createdAt: -1 }).limit(50),

            Problem.find({
                ...baseFilter,
                priority: { $in: ['High', 'Urgent'] },
                status: { $in: ['Open', 'Assigned', 'In Progress'] }
            }).sort({ createdAt: -1 }).limit(20),

            Problem.find({
                ...baseFilter,
                createdAt: { $gte: oneDayAgo }
            }).sort({ createdAt: -1 }).limit(20),

            Problem.find({
                ...baseFilter,
                assignedTo: req.user._id,
                status: 'Resolved',
                feedbackStatus: 'pending'
            }).sort({ updatedAt: -1 }).limit(20)
        ]);

        const myStats = await Problem.aggregate([
            {
                $match: {
                    assignedTo: req.user._id,
                    communityId: req.user.communityId,
                    feedbackStatus: 'submitted',
                    'feedback.rating': { $ne: null }
                }
            },
            {
                $group: {
                    _id: null,
                    totalFeedback: { $sum: 1 },
                    averageRating: { $avg: '$feedback.rating' }
                }
            }
        ]);

        const stats = myStats.length > 0 ? {
            totalFeedback: myStats[0].totalFeedback,
            averageRating: Math.round(myStats[0].averageRating * 10) / 10
        } : { totalFeedback: 0, averageRating: 0 };

        return res.status(200).json({
            success: true,
            assignedToMe: assignedToMe.map(p => p.toPublicJSON()),
            highPriority: highPriority.map(p => p.toPublicJSON()),
            recent: recent.map(p => p.toPublicJSON()),
            awaitingFeedback: awaitingFeedback.map(p => p.toPublicJSON()),
            stats
        });

    } catch (error) {
        console.error('❌ [MAINTAINER DASHBOARD] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// GET MY WORK (priority-sorted for maintainer home)
// ═════════════════════════════════════════════
exports.getMyWork = async (req, res) => {
    try {
        if (!['Staff', 'Admin'].includes(req.user.role)) {
            return res.status(403).json({
                success: false,
                message: 'Access denied — Staff/Admin only'
            });
        }

        const baseFilter = {
            isDeleted: false,
            communityId: req.user.communityId,
            assignedTo: req.user._id
        };

        const notFinished = await Problem.find({
            ...baseFilter,
            status: { $in: ['Assigned', 'In Progress'] }
        }).sort({ priority: -1, createdAt: -1 });

        const finished = await Problem.find({
            ...baseFilter,
            status: 'Resolved'
        }).sort({ resolvedAt: -1 }).limit(30);

        const priorityOrder = { 'Urgent': 4, 'High': 3, 'Medium': 2, 'Low': 1 };
        const sortedNotFinished = notFinished.sort((a, b) => {
            const diff = (priorityOrder[b.priority] || 0) - (priorityOrder[a.priority] || 0);
            if (diff !== 0) return diff;
            return new Date(b.createdAt) - new Date(a.createdAt);
        });

        return res.status(200).json({
            success: true,
            notFinished: sortedNotFinished.map(p => p.toPublicJSON()),
            finished: finished.map(p => p.toPublicJSON()),
            counts: {
                notFinished: sortedNotFinished.length,
                finished: finished.length
            }
        });

    } catch (error) {
        console.error('❌ [GET MY WORK] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// STANDARD PROBLEM OPERATIONS
// ═════════════════════════════════════════════

exports.getProblems = async (req, res) => {
    try {
        const limit = Math.min(parseInt(req.query.limit) || 30, 100);
        const skip = parseInt(req.query.skip) || 0;
        const filter = scopedProblemFilter(req);

        const problems = await Problem.find(filter)
            .sort({ createdAt: -1 })
            .skip(skip)
            .limit(limit);

        return res.status(200).json({
            success: true,
            count: problems.length,
            communityId: req.user.communityId,
            problems: problems.map(p => p.toPublicJSON())
        });
    } catch (error) {
        console.error('❌ [GET PROBLEMS] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.getProblemById = async (req, res) => {
    try {
        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'This problem belongs to a different community' });
        }
        return res.status(200).json({ success: true, problem: problem.toPublicJSON() });
    } catch (error) {
        console.error('❌ [GET PROBLEM] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.updateProblem = async (req, res) => {
    try {
        const { title, description, location, category, priority } = req.body;
        const problem = await Problem.findById(req.params.id);

        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isReporter = problem.reporterId.toString() === req.user._id.toString();
        const isAdmin = req.user.role === 'Admin' || req.user.role === 'SuperAdmin';

        if (!isReporter && !isAdmin) {
            return res.status(403).json({
                success: false,
                message: 'Only the reporter or an Admin can edit this problem'
            });
        }

        if (title) problem.title = title.trim();
        if (description) problem.description = description.trim();
        if (location) problem.location = location.trim();
        if (category) problem.category = category;
        if (priority) problem.priority = priority;

        problem.editedAt = new Date();
        await problem.save();

        return res.status(200).json({
            success: true,
            message: 'Problem updated',
            problem: problem.toPublicJSON()
        });
    } catch (error) {
        console.error('❌ [UPDATE PROBLEM] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.toggleLike = async (req, res) => {
    try {
        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const uid = req.user._id.toString();
        const idx = problem.likes.findIndex(l => l.toString() === uid);

        let liked;
        if (idx === -1) {
            problem.likes.push(req.user._id);
            liked = true;
        } else {
            problem.likes.splice(idx, 1);
            liked = false;
        }
        await problem.save();

        if (liked && problem.reporterId.toString() !== uid) {
            await createNotification({
                communityId: problem.communityId,
                recipientId: problem.reporterId,
                recipientEmail: problem.reporterEmail,
                category: 'PROBLEM_LIKED',
                title: 'Your report was liked',
                body: `${req.user.name} liked your report "${problem.title}"`,
                relatedProblemId: problem._id,
                actorId: req.user._id,
                actorName: req.user.name,
                actorAvatar: req.user.avatarUrl || ''
            });
        }

        return res.status(200).json({ success: true, liked, likesCount: problem.likes.length });
    } catch (error) {
        console.error('❌ [LIKE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.addComment = async (req, res) => {
    try {
        const { content } = req.body;
        if (!content || !content.trim()) {
            return res.status(400).json({ success: false, message: 'Comment cannot be empty' });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const comment = {
            authorId: req.user._id,
            authorName: req.user.name,
            authorRole: req.user.role,
            authorAvatar: req.user.avatarUrl || '',
            content: content.trim()
        };
        problem.comments.push(comment);
        await problem.save();

        const newComment = problem.comments[problem.comments.length - 1];

        if (problem.reporterId.toString() !== req.user._id.toString()) {
            await createNotification({
                communityId: problem.communityId,
                recipientId: problem.reporterId,
                recipientEmail: problem.reporterEmail,
                category: 'PROBLEM_COMMENTED',
                title: `${req.user.name} commented on your report`,
                body: `"${content.trim().slice(0, 100)}"`,
                relatedProblemId: problem._id,
                actorId: req.user._id,
                actorName: req.user.name,
                actorAvatar: req.user.avatarUrl || ''
            });
        }

        return res.status(201).json({
            success: true,
            message: 'Comment added',
            comment: {
                id: newComment._id,
                authorName: newComment.authorName,
                authorRole: newComment.authorRole,
                authorAvatar: newComment.authorAvatar,
                content: newComment.content,
                createdAt: newComment.createdAt
            }
        });
    } catch (error) {
        console.error('❌ [COMMENT] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.assignProblem = async (req, res) => {
    try {
        const { maintainerId } = req.body;

        if (!['Admin', 'SuperAdmin'].includes(req.user.role)) {
            return res.status(403).json({ success: false, message: 'Only Admin can assign' });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const maintainer = await User.findById(maintainerId);
        if (!maintainer) {
            return res.status(404).json({ success: false, message: 'Maintainer not found' });
        }
        if (req.user.role !== 'SuperAdmin' &&
            maintainer.communityId?.toString() !== problem.communityId.toString()) {
            return res.status(400).json({
                success: false,
                message: 'Cannot assign to a maintainer from a different community'
            });
        }

        problem.assignedTo = maintainer._id;
        problem.assignedToName = maintainer.name;
        problem.assignedAt = new Date();
        problem.status = 'Assigned';
        problem.autoAssigned = false;
        problem.statusHistory.push({
            status: 'Assigned',
            changedBy: req.user._id,
            changedByName: req.user.name,
            note: `Manually assigned to ${maintainer.name}`
        });
        await problem.save();

        await createNotification({
            communityId: problem.communityId,
            recipientId: maintainer._id,
            recipientEmail: maintainer.email,
            category: 'PROBLEM_ASSIGNED',
            title: 'New task assigned to you',
            body: `You've been assigned: "${problem.title}" at ${problem.location}`,
            relatedProblemId: problem._id,
            actorId: req.user._id,
            actorName: req.user.name,
            actorAvatar: req.user.avatarUrl || ''
        });

        await createNotification({
            communityId: problem.communityId,
            recipientId: problem.reporterId,
            recipientEmail: problem.reporterEmail,
            category: 'PROBLEM_UPDATED',
            title: 'Your report has been assigned',
            body: `"${problem.title}" has been assigned to ${maintainer.name}`,
            relatedProblemId: problem._id,
            actorId: req.user._id,
            actorName: req.user.name,
            actorAvatar: req.user.avatarUrl || ''
        });

        return res.status(200).json({
            success: true,
            message: `Assigned to ${maintainer.name}`,
            problem: problem.toPublicJSON()
        });
    } catch (error) {
        console.error('❌ [ASSIGN] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.updateStatus = async (req, res) => {
    try {
        const { status, note, afterImage } = req.body;

        const validStatuses = ['Open', 'Assigned', 'In Progress', 'Resolved', 'Rejected'];
        if (status && !validStatuses.includes(status)) {
            return res.status(400).json({ success: false, message: 'Invalid status' });
        }

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isAdmin = ['Admin', 'SuperAdmin'].includes(req.user.role);
        const isAssigned = problem.assignedTo?.toString() === req.user._id.toString();

        if (!isAdmin && !isAssigned) {
            return res.status(403).json({
                success: false,
                message: 'Only Admin or the assigned Maintainer can update status'
            });
        }

        if (status) problem.status = status;
        if (afterImage) problem.afterImage = afterImage;

        problem.statusHistory.push({
            status: status || problem.status,
            changedBy: req.user._id,
            changedByName: req.user.name,
            note: note || ''
        });
        await problem.save();

        const category = status === 'Resolved' ? 'PROBLEM_RESOLVED' : 'PROBLEM_UPDATED';
        await createNotification({
            communityId: problem.communityId,
            recipientId: problem.reporterId,
            recipientEmail: problem.reporterEmail,
            category,
            title: status === 'Resolved' ? `Your report has been fixed 🎉` : `Your report status: ${status}`,
            body: `"${problem.title}"${note ? ` — ${note}` : ''}`,
            relatedProblemId: problem._id,
            actorId: req.user._id,
            actorName: req.user.name,
            actorAvatar: req.user.avatarUrl || ''
        });

        return res.status(200).json({
            success: true,
            message: 'Status updated',
            problem: problem.toPublicJSON()
        });
    } catch (error) {
        console.error('❌ [STATUS UPDATE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.deleteProblem = async (req, res) => {
    try {
        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }
        if (!userInSameCommunity(req, problem.communityId)) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const isReporter = problem.reporterId.toString() === req.user._id.toString();
        const isAdmin = ['Admin', 'SuperAdmin'].includes(req.user.role);

        if (!isReporter && !isAdmin) {
            return res.status(403).json({
                success: false,
                message: 'Only the reporter or an Admin can delete this'
            });
        }

        problem.isDeleted = true;
        await problem.save();

        return res.status(200).json({ success: true, message: 'Problem deleted' });
    } catch (error) {
        console.error('❌ [DELETE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.getMyProblems = async (req, res) => {
    try {
        const filter = scopedProblemFilter(req, { reporterId: req.user._id });
        const problems = await Problem.find(filter).sort({ createdAt: -1 });
        return res.status(200).json({
            success: true,
            problems: problems.map(p => p.toPublicJSON())
        });
    } catch (error) {
        console.error('❌ [GET MINE] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

exports.getAssignedProblems = async (req, res) => {
    try {
        const filter = scopedProblemFilter(req, { assignedTo: req.user._id });
        const problems = await Problem.find(filter).sort({ createdAt: -1 });
        return res.status(200).json({
            success: true,
            problems: problems.map(p => p.toPublicJSON())
        });
    } catch (error) {
        console.error('❌ [GET ASSIGNED] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// ADMIN — CREATE PROBLEM ON BEHALF OF RESIDENT
// ═════════════════════════════════════════════
exports.createProblemOnBehalf = async (req, res) => {
    try {
        if (!['Admin', 'SuperAdmin'].includes(req.user.role)) {
            return res.status(403).json({ success: false, message: 'Admin only' });
        }

        const { reporterId, title, category, location, description, priority, beforeImage } = req.body;

        console.log(`\n👤 [ADMIN CREATE ON BEHALF] By: ${req.user.email}`);

        if (!reporterId) {
            return res.status(400).json({ success: false, message: 'reporterId is required' });
        }

        const reporter = await User.findById(reporterId);
        if (!reporter) {
            return res.status(404).json({ success: false, message: 'Reporter (resident) not found' });
        }

        if (req.user.role === 'Admin') {
            if (reporter.communityId?.toString() !== req.user.communityId?.toString()) {
                return res.status(400).json({ success: false, message: 'Reporter must be in your community' });
            }
        }

        if (!title || !location || !description) {
            return res.status(400).json({ success: false, message: 'Title, location, and description are required' });
        }

        let yoloResult = { success: false, detections: [], modelLoaded: '', inferenceMs: 0 };

        if (beforeImage && isYoloConfigured()) {
            try {
                yoloResult = await detectObjects(beforeImage);
            } catch (err) {}
        }

        let finalCategory = category || 'Other';
        if ((!category || category === 'Other') && yoloResult.success && yoloResult.detections.length > 0) {
            const topDetection = yoloResult.detections.reduce(
                (best, d) => (d.confidence > (best.confidence || 0) ? d : best), {}
            );
            const inferred = mapClassToCategory(topDetection.class);
            if (inferred && inferred !== 'Other') finalCategory = inferred;
        }

        let diagnostic = {
            summary: '', recommendedAction: '',
            detectedObjects: yoloResult.detections.map(d => d.class),
            severity: computeSeverity(yoloResult.detections) || 'Medium',
            estimatedHours: 2, confidenceScore: 60
        };

        if (isGeminiConfigured()) {
            try {
                diagnostic = await analyzeProblem(beforeImage || '', title, description, location, finalCategory, yoloResult.detections);
            } catch (err) {}
        }

        const problem = new Problem({
            communityId: reporter.communityId || req.user.communityId,
            communityName: reporter.communityName || req.user.communityName || '',
            title: title.trim(),
            category: finalCategory,
            location: location.trim(),
            description: description.trim(),
            priority: priority || 'Medium',
            beforeImage: beforeImage || '',
            reporterId: reporter._id,
            reporterName: reporter.name,
            reporterRole: reporter.role,
            reporterEmail: reporter.email,
            reporterAvatar: reporter.avatarUrl || '',
            createdByAdmin: req.user._id,
            createdByAdminName: req.user.name,
            yoloDetections: yoloResult.success ? yoloResult.detections : [],
            yoloModelName: yoloResult.modelLoaded || '',
            yoloInferenceMs: yoloResult.inferenceMs || 0,
            aiDiagnostic: diagnostic,
            status: 'Open',
            statusHistory: [{
                status: 'Open',
                changedBy: req.user._id,
                changedByName: req.user.name,
                note: `Created by Admin on behalf of ${reporter.name}`
            }]
        });

        await problem.save();

        try { await autoAssignToLeastBusyMaintainer(problem); } catch (err) {}

        await createNotification({
            communityId: problem.communityId,
            recipientId: reporter._id,
            recipientEmail: reporter.email,
            category: 'PROBLEM_UPDATED',
            title: 'A problem was created for you',
            body: `An admin has logged this report on your behalf: "${problem.title}"`,
            relatedProblemId: problem._id,
            actorId: req.user._id,
            actorName: req.user.name,
            actorAvatar: req.user.avatarUrl || ''
        });

        const freshProblem = await Problem.findById(problem._id);

        return res.status(201).json({
            success: true,
            message: 'Problem created on behalf of reporter',
            problem: freshProblem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [ADMIN CREATE ON BEHALF] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};

// ═════════════════════════════════════════════
// ADMIN — FULL EDIT OF ANY PROBLEM
// ═════════════════════════════════════════════
exports.adminUpdateProblem = async (req, res) => {
    try {
        if (!['Admin', 'SuperAdmin'].includes(req.user.role)) {
            return res.status(403).json({ success: false, message: 'Admin only' });
        }

        const {
            title, description, location, category, priority,
            status, assignedTo, beforeImage, afterImage, feedbackStatus
        } = req.body;

        const problem = await Problem.findById(req.params.id);
        if (!problem || problem.isDeleted) {
            return res.status(404).json({ success: false, message: 'Problem not found' });
        }

        if (req.user.role === 'Admin' && problem.communityId?.toString() !== req.user.communityId?.toString()) {
            return res.status(403).json({ success: false, message: 'Access denied' });
        }

        const changes = [];

        if (title !== undefined && title.trim() !== problem.title) { problem.title = title.trim(); changes.push('title'); }
        if (description !== undefined && description.trim() !== problem.description) { problem.description = description.trim(); changes.push('description'); }
        if (location !== undefined && location.trim() !== problem.location) { problem.location = location.trim(); changes.push('location'); }
        if (category !== undefined && category !== problem.category) { problem.category = category; changes.push('category'); }
        if (priority !== undefined && priority !== problem.priority) { problem.priority = priority; changes.push('priority'); }
        if (beforeImage !== undefined) { problem.beforeImage = beforeImage; changes.push('beforeImage'); }
        if (afterImage !== undefined) { problem.afterImage = afterImage; changes.push('afterImage'); }

        if (status !== undefined && status !== problem.status) {
            const validStatuses = ['Open', 'Assigned', 'In Progress', 'Resolved', 'Rejected'];
            if (!validStatuses.includes(status)) {
                return res.status(400).json({ success: false, message: 'Invalid status' });
            }
            problem.status = status;
            problem.statusHistory.push({
                status, changedBy: req.user._id, changedByName: req.user.name, note: 'Admin override'
            });
            changes.push('status');
        }

        if (assignedTo !== undefined) {
            if (assignedTo === null || assignedTo === '') {
                problem.assignedTo = null;
                problem.assignedToName = '';
                problem.assignedAt = null;
                changes.push('unassigned');
            } else {
                const newMaintainer = await User.findById(assignedTo);
                if (!newMaintainer || !['Staff', 'Admin'].includes(newMaintainer.role)) {
                    return res.status(400).json({ success: false, message: 'Invalid maintainer' });
                }
                if (req.user.role === 'Admin' && newMaintainer.communityId?.toString() !== problem.communityId?.toString()) {
                    return res.status(400).json({ success: false, message: 'Maintainer must be in the same community' });
                }

                const prevId = problem.assignedTo?.toString();
                if (prevId !== newMaintainer._id.toString()) {
                    problem.reassignHistory.push({
                        fromId: problem.assignedTo || null,
                        fromName: problem.assignedToName || 'Unassigned',
                        toId: newMaintainer._id,
                        toName: newMaintainer.name,
                        byId: req.user._id,
                        byName: req.user.name,
                        reason: 'Admin changed assignment via admin editor',
                        wasAuto: false
                    });

                    problem.assignedTo = newMaintainer._id;
                    problem.assignedToName = newMaintainer.name;
                    problem.assignedAt = new Date();
                    if (!problem.firstAssignedAt) problem.firstAssignedAt = new Date();
                    if (problem.status === 'Open') problem.status = 'Assigned';
                    changes.push('assignedTo');
                }
            }
        }

        if (feedbackStatus !== undefined && ['pending', 'submitted'].includes(feedbackStatus)) {
            if (feedbackStatus !== problem.feedbackStatus) {
                problem.feedbackStatus = feedbackStatus;
                changes.push('feedbackStatus');
            }
        }

        problem.editedAt = new Date();
        await problem.save();

        if (changes.includes('status') || changes.includes('assignedTo')) {
            await createNotification({
                communityId: problem.communityId,
                recipientId: problem.reporterId,
                recipientEmail: problem.reporterEmail,
                category: 'PROBLEM_UPDATED',
                title: 'Your report was updated by Admin',
                body: `"${problem.title}" — ${changes.join(', ')}`,
                relatedProblemId: problem._id,
                actorId: req.user._id,
                actorName: req.user.name,
                actorAvatar: req.user.avatarUrl || ''
            });
        }

        return res.status(200).json({
            success: true,
            message: `Updated: ${changes.join(', ') || 'no changes'}`,
            problem: problem.toPublicJSON()
        });

    } catch (error) {
        console.error('❌ [ADMIN UPDATE PROBLEM] Error:', error);
        return res.status(500).json({ success: false, message: error.message || 'Server error' });
    }
};
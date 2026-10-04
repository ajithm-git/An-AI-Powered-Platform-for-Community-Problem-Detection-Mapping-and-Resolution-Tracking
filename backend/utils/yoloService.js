/**
 * YOLO Service (Node.js wrapper)
 * ─────────────────────────────────────────────
 * Spawns the Python `detect.py` script and pipes
 * the image through stdin → parses JSON from stdout.
 *
 * Now also logs YOLO detections to the Node.js terminal
 * so you can see real-time inference results in your backend console.
 */

const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

// ─────────────────────────────────────────────
// Track active child processes for cleanup
// ─────────────────────────────────────────────
const activeChildren = new Set();

process.on('exit', () => {
    for (const child of activeChildren) {
        try { child.kill('SIGKILL'); } catch (_) {}
    }
});
process.on('SIGINT', () => {
    for (const child of activeChildren) {
        try { child.kill('SIGKILL'); } catch (_) {}
    }
    process.exit(0);
});

// ─────────────────────────────────────────────
// Resolve paths
// ─────────────────────────────────────────────
const YOLO_DIR = path.join(__dirname, '..', 'yolo');
const DETECT_SCRIPT = path.join(YOLO_DIR, 'detect.py');

function resolvePythonPath() {
    if (process.env.YOLO_PYTHON) {
        return process.env.YOLO_PYTHON;
    }
    const winVenv = path.join(YOLO_DIR, '.venv', 'Scripts', 'python.exe');
    if (fs.existsSync(winVenv)) return winVenv;
    const unixVenv = path.join(YOLO_DIR, '.venv', 'bin', 'python');
    if (fs.existsSync(unixVenv)) return unixVenv;
    if (process.platform === 'win32') return 'py';
    return 'python';
}

const PYTHON_PATH = resolvePythonPath();

// ─────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────
const DEFAULT_CONF = parseFloat(process.env.YOLO_CONF_THRESHOLD || '0.35');
const DEFAULT_IOU = parseFloat(process.env.YOLO_IOU_THRESHOLD || '0.45');
const MAX_TIMEOUT_MS = parseInt(process.env.YOLO_TIMEOUT_MS || '60000', 10);

// ─────────────────────────────────────────────
// Pretty-print detections to Node.js console
// ─────────────────────────────────────────────
function logDetections(detections, model, inferenceMs) {
    if (!Array.isArray(detections)) return;

    if (detections.length === 0) {
        console.log(`   ⚪ [YOLO] No objects detected (model=${model}, ${inferenceMs}ms)`);
        return;
    }

    console.log(`   📦 [YOLO] ${detections.length} object(s) detected (model=${model}, ${inferenceMs}ms):`);
    for (const d of detections) {
        const pct = (d.confidence * 100).toFixed(1);
        const box = d.bbox
            ? ` [${d.bbox.x1.toFixed(0)},${d.bbox.y1.toFixed(0)} → ${d.bbox.x2.toFixed(0)},${d.bbox.y2.toFixed(0)}]`
            : '';
        console.log(`      • ${d.class} (${pct}%)${box}`);
    }
}

// ─────────────────────────────────────────────
// Check if YOLO service is ready
// ─────────────────────────────────────────────
function isYoloConfigured() {
    return fs.existsSync(DETECT_SCRIPT);
}

// ─────────────────────────────────────────────
// Get model info (cached)
// ─────────────────────────────────────────────
let cachedModelInfo = null;

function getModelInfoSync() {
    if (cachedModelInfo) return cachedModelInfo;

    if (!isYoloConfigured()) {
        return {
            configured: false,
            message: `YOLO detect.py not found at ${DETECT_SCRIPT}`,
            pythonPath: PYTHON_PATH,
            yoloDir: YOLO_DIR
        };
    }

    const weightsDir = path.join(YOLO_DIR, 'weights_yolo11n');
    let weightFiles = [];
    try {
        if (fs.existsSync(weightsDir)) {
            weightFiles = fs.readdirSync(weightsDir).filter(f => f.endsWith('.pt') || f.endsWith('.onnx'));
        }
        const localFiles = fs.readdirSync(YOLO_DIR).filter(f => f.endsWith('.pt'));
        weightFiles = [...new Set([...weightFiles, ...localFiles])];
    } catch (_) {}

    cachedModelInfo = {
        configured: true,
        pythonPath: PYTHON_PATH,
        detectScript: DETECT_SCRIPT,
        yoloDir: YOLO_DIR,
        weightFiles,
        confThreshold: DEFAULT_CONF,
        iouThreshold: DEFAULT_IOU,
        timeoutMs: MAX_TIMEOUT_MS
    };
    return cachedModelInfo;
}

// ─────────────────────────────────────────────
// Query Python for live model info (--info flag)
// ─────────────────────────────────────────────
function fetchModelInfo() {
    return new Promise((resolve) => {
        if (!isYoloConfigured()) {
            return resolve({ success: false, error: 'YOLO not configured' });
        }

        let stdout = '';
        let stderr = '';
        let child;

        try {
            child = spawn(PYTHON_PATH, [DETECT_SCRIPT, '--info'], {
                cwd: YOLO_DIR,
                env: { ...process.env, PYTHONIOENCODING: 'utf-8' }
            });
            activeChildren.add(child);
        } catch (err) {
            return resolve({ success: false, error: err.message });
        }

        const timer = setTimeout(() => {
            try { child.kill('SIGKILL'); } catch (_) {}
            activeChildren.delete(child);
            resolve({ success: false, error: 'Model info timed out' });
        }, 15000);

        child.stdout.on('data', (c) => { stdout += c.toString(); });
        child.stderr.on('data', (c) => { stderr += c.toString(); });

        child.on('close', () => {
            clearTimeout(timer);
            activeChildren.delete(child);
            try {
                const parsed = JSON.parse(stdout.trim());
                resolve(parsed);
            } catch (_) {
                resolve({ success: false, error: 'Invalid JSON from --info', raw: stdout.slice(0, 200) });
            }
        });
    });
}

// ─────────────────────────────────────────────
// Main detect function
// ─────────────────────────────────────────────
function detectObjects(imageBase64, opts = {}) {
    return new Promise((resolve) => {
        if (!imageBase64 || typeof imageBase64 !== 'string') {
            return resolve({ success: false, error: 'imageBase64 is required' });
        }
        if (!isYoloConfigured()) {
            return resolve({
                success: false,
                error: `YOLO service not configured. Missing: ${DETECT_SCRIPT}`
            });
        }

        const verbose = opts.verbose !== false; // default true

        const payload = JSON.stringify({
            imageBase64,
            confThreshold: typeof opts.confThreshold === 'number' ? opts.confThreshold : DEFAULT_CONF,
            iouThreshold: typeof opts.iouThreshold === 'number' ? opts.iouThreshold : DEFAULT_IOU
        });

        // Build args — add --verbose if enabled
        const args = [DETECT_SCRIPT];
        if (verbose) args.push('--verbose');

        // Optional: save annotated image
        if (opts.saveAnnotated) {
            args.push('--save-annotated', opts.saveAnnotated);
        }

        const started = Date.now();
        let stdout = '';
        let stderr = '';
        let finished = false;

        let child;
        try {
            child = spawn(PYTHON_PATH, args, {
                cwd: YOLO_DIR,
                env: {
                    ...process.env,
                    PYTHONIOENCODING: 'utf-8',
                    PYTHONUNBUFFERED: '1'
                }
            });
            activeChildren.add(child);
        } catch (spawnErr) {
            return resolve({ success: false, error: `Failed to spawn python: ${spawnErr.message}` });
        }

        const killTimer = setTimeout(() => {
            if (!finished) {
                finished = true;
                try { child.kill('SIGKILL'); } catch (_) {}
                activeChildren.delete(child);
                resolve({
                    success: false,
                    error: `YOLO inference timed out after ${MAX_TIMEOUT_MS}ms`
                });
            }
        }, MAX_TIMEOUT_MS);

        child.stdout.on('data', (chunk) => { stdout += chunk.toString('utf8'); });
        child.stderr.on('data', (chunk) => {
            const text = chunk.toString('utf8');
            stderr += text;
            // Stream Python's verbose logs live to Node console
            if (verbose) {
                text.split('\n').filter(Boolean).forEach(line => {
                    console.log(`   🐍 ${line}`);
                });
            }
        });

        child.on('error', (err) => {
            if (finished) return;
            finished = true;
            clearTimeout(killTimer);
            activeChildren.delete(child);
            resolve({ success: false, error: `Python process error: ${err.message}` });
        });

        child.on('close', (code) => {
            if (finished) return;
            finished = true;
            clearTimeout(killTimer);
            activeChildren.delete(child);

            const elapsed = Date.now() - started;

            let parsed = null;
            try {
                parsed = JSON.parse(stdout.trim());
            } catch (e) {
                const matches = stdout.match(/\{[\s\S]*\}/g);
                if (matches && matches.length > 0) {
                    try { parsed = JSON.parse(matches[matches.length - 1]); } catch (_) {}
                }
            }

            if (!parsed) {
                return resolve({
                    success: false,
                    error: `Invalid JSON from Python (exit=${code}). stderr: ${stderr.slice(0, 300)}`
                });
            }

            if (!parsed.success) {
                return resolve({
                    success: false,
                    error: parsed.error || `Python exited with code ${code}`,
                    traceback: parsed.traceback
                });
            }

            // ✨ Log detections to Node.js console (visible in terminal)
            logDetections(
                parsed.detections || [],
                parsed.modelLoaded || 'unknown',
                parsed.inferenceMs || elapsed
            );

            resolve({
                success: true,
                detections: parsed.detections || [],
                inferenceMs: parsed.inferenceMs || elapsed,
                modelLoaded: parsed.modelLoaded || 'unknown',
                imageSize: parsed.imageSize || null
            });
        });

        try {
            child.stdin.write(payload);
            child.stdin.end();
        } catch (err) {
            if (!finished) {
                finished = true;
                clearTimeout(killTimer);
                activeChildren.delete(child);
                resolve({ success: false, error: `Failed to write to Python stdin: ${err.message}` });
            }
        }
    });
}

// ─────────────────────────────────────────────
// Map YOLO class → Community AI category
// ─────────────────────────────────────────────
const CLASS_TO_CATEGORY = {
    'garbage': 'Cleanliness',
    'water_leakage': 'Plumbing',
    'flooding': 'Plumbing',
    'road_floor_damage': 'Structural'
};

function mapClassToCategory(className) {
    if (!className) return 'Other';
    const normalized = className.toLowerCase().trim();
    return CLASS_TO_CATEGORY[normalized] || 'Other';
}

// ─────────────────────────────────────────────
// Compute overall severity from detections
// ─────────────────────────────────────────────
function computeSeverity(detections) {
    if (!Array.isArray(detections) || detections.length === 0) return 'Low';

    let maxConf = 0;
    let hasCritical = false;
    let hasHigh = false;

    for (const d of detections) {
        maxConf = Math.max(maxConf, d.confidence || 0);
        const cls = (d.class || '').toLowerCase();
        const conf = d.confidence || 0;

        if (cls === 'flooding' && conf > 0.5) hasCritical = true;
        if ((cls === 'water_leakage' || cls === 'road_floor_damage') && conf > 0.6) hasHigh = true;
    }

    if (hasCritical || maxConf > 0.9) return 'Critical';
    if (hasHigh || maxConf > 0.7) return 'High';
    if (maxConf > 0.5) return 'Medium';
    return 'Low';
}

// ═════════════════════════════════════════════
// Verify Resolution
// ═════════════════════════════════════════════
function verifyResolution(beforeDetections = [], afterDetections = [], opts = {}) {
    const minConf = typeof opts.confThreshold === 'number' ? opts.confThreshold : DEFAULT_CONF;

    const filterByConf = (detections) =>
        (Array.isArray(detections) ? detections : [])
            .filter(d => d && typeof d.confidence === 'number' && d.confidence >= minConf);

    const before = filterByConf(beforeDetections);
    const after = filterByConf(afterDetections);

    const beforeClasses = [...new Set(before.map(d => String(d.class).toLowerCase()))];
    const afterClasses = [...new Set(after.map(d => String(d.class).toLowerCase()))];

    const stillPresent = afterClasses.filter(c => beforeClasses.includes(c));
    const newIssues = afterClasses.filter(c => !beforeClasses.includes(c));
    const resolved = beforeClasses.filter(c => !afterClasses.includes(c));

    let verdict, confidence, reason = '';

    if (beforeClasses.length === 0 && afterClasses.length === 0) {
        verdict = 'UNVERIFIABLE';
        confidence = 0;
        reason = 'No objects detected in either before or after image';
    } else if (beforeClasses.length === 0) {
        verdict = 'UNVERIFIABLE';
        confidence = 0;
        reason = 'No objects detected in the before image';
    } else if (stillPresent.length === 0 && newIssues.length === 0) {
        verdict = 'VERIFIED';
        confidence = 95;
        reason = 'All previously detected issues are gone';
    } else if (stillPresent.length === 0 && newIssues.length > 0) {
        verdict = 'PARTIAL';
        confidence = 60;
        reason = 'Original issues fixed, but new objects detected';
    } else if (stillPresent.length > 0 && resolved.length > 0) {
        verdict = 'PARTIAL';
        confidence = 50;
        reason = 'Some issues fixed, some still present';
    } else {
        verdict = 'NOT_VERIFIED';
        confidence = 25;
        reason = 'The same issues are still visible';
    }

    const summary = summarizeVerification({
        verdict, beforeClasses, afterClasses, stillPresent, newIssues, resolved
    });

    return {
        verdict,
        confidence,
        summary,
        reason,
        beforeClasses,
        afterClasses,
        stillPresent,
        newIssues,
        resolved,
        beforeCount: before.length,
        afterCount: after.length,
        thresholds: { confThreshold: minConf }
    };
}

function summarizeVerification({
    verdict, beforeClasses, afterClasses, stillPresent, newIssues, resolved
}) {
    const fmt = (arr) => arr.length === 0 ? 'none' : arr.join(', ');

    if (verdict === 'VERIFIED') {
        if (beforeClasses.length === 1) {
            return `✅ Verified: "${beforeClasses[0].replace('_', ' ')}" no longer detected.`;
        }
        return `✅ Verified: All previously detected issues resolved (${fmt(beforeClasses)}).`;
    }

    if (verdict === 'NOT_VERIFIED') {
        return `❌ Not verified: "${fmt(stillPresent)}" is still visible. Please re-inspect.`;
    }

    if (verdict === 'PARTIAL') {
        const parts = [];
        if (resolved.length > 0) parts.push(`fixed: ${fmt(resolved)}`);
        if (stillPresent.length > 0) parts.push(`still present: ${fmt(stillPresent)}`);
        if (newIssues.length > 0) parts.push(`new: ${fmt(newIssues)}`);
        return `⚠️ Partial: ${parts.join(' · ')}`;
    }

    return `ℹ️ Unverifiable: No matching detections (before: ${fmt(beforeClasses)}, after: ${fmt(afterClasses)}).`;
}

module.exports = {
    detectObjects,
    verifyResolution,
    summarizeVerification,
    isYoloConfigured,
    getModelInfo: getModelInfoSync,
    fetchModelInfo,
    mapClassToCategory,
    computeSeverity,
    CLASS_TO_CATEGORY
};
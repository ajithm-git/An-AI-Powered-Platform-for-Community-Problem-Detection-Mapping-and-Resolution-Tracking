/**
 * Gemini AI Service
 * ─────────────────────────────────────────────
 * 1. analyzeProblem()         — Full diagnostic (saved problems)
 * 2. draftProblemFields()     — Draft title/description for user
 * 3. generateWorkChecklist()  — AI steps for maintainer
 * 4. chatReply()              — Chatbot
 *
 * Requires GEMINI_API_KEY in .env
 */

const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-1.5-flash-latest';
const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta';

// ─────────────────────────────────────────────
// Node fetch fallback (Node < 18)
// ─────────────────────────────────────────────
let fetchFn = globalThis.fetch;
if (!fetchFn) {
    try {
        // Node 16-17 with node-fetch installed
        fetchFn = require('node-fetch');
    } catch (_) {
        console.log('ℹ️  [GEMINI] Native fetch not available. Please upgrade to Node 18+ or install node-fetch.');
    }
}

// ─────────────────────────────────────────────
// Config check
// ─────────────────────────────────────────────
function isGeminiConfigured() {
    return Boolean(GEMINI_API_KEY && GEMINI_API_KEY.length > 10 && fetchFn);
}

// ─────────────────────────────────────────────
// Sleep helper
// ─────────────────────────────────────────────
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// ─────────────────────────────────────────────
// Low-level call (with retry for transient errors)
// ─────────────────────────────────────────────
async function callGemini(parts, systemInstruction = null, temperature = 0.7, retries = 1) {
    if (!isGeminiConfigured()) {
        throw new Error('Gemini API is not configured. Set GEMINI_API_KEY in .env');
    }

    const url = `${GEMINI_BASE_URL}/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`;

    const body = {
        contents: [{ role: 'user', parts }],
        generationConfig: {
            temperature,
            maxOutputTokens: 2048,
            topP: 0.95,
            topK: 40
        }
    };

    if (systemInstruction) {
        body.systemInstruction = { parts: [{ text: systemInstruction }] };
    }

    let lastError = null;

    for (let attempt = 0; attempt <= retries; attempt++) {
        try {
            const response = await fetchFn(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            // Handle transient errors (429, 500, 502, 503, 504)
            if (!response.ok) {
                const errorText = await response.text();
                const isTransient = [429, 500, 502, 503, 504].includes(response.status);

                if (isTransient && attempt < retries) {
                    console.log(`⚠️ [GEMINI] Transient error ${response.status}, retrying...`);
                    await sleep(1000 * (attempt + 1)); // Exponential backoff
                    lastError = new Error(`Gemini API failed: ${response.status}`);
                    continue;
                }

                console.error('❌ Gemini API error:', response.status, errorText.slice(0, 300));
                throw new Error(`Gemini API failed: ${response.status}`);
            }

            const data = await response.json();
            const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
            if (!text) throw new Error('Gemini returned no text');
            return text;

        } catch (err) {
            lastError = err;
            if (attempt < retries && err.message.includes('fetch')) {
                await sleep(1000);
                continue;
            }
            break;
        }
    }

    throw lastError || new Error('Gemini call failed');
}

// ─────────────────────────────────────────────
// Robust JSON extractor (balanced braces)
// ─────────────────────────────────────────────
function extractJson(text) {
    if (!text) return null;

    // Try direct parse first
    try {
        return JSON.parse(text);
    } catch (_) {}

    // Find the first balanced { ... } block
    const start = text.indexOf('{');
    if (start === -1) return null;

    let depth = 0;
    let inString = false;
    let escapeNext = false;

    for (let i = start; i < text.length; i++) {
        const ch = text[i];

        if (escapeNext) {
            escapeNext = false;
            continue;
        }

        if (ch === '\\') {
            escapeNext = true;
            continue;
        }

        if (ch === '"') {
            inString = !inString;
            continue;
        }

        if (inString) continue;

        if (ch === '{') depth++;
        else if (ch === '}') {
            depth--;
            if (depth === 0) {
                const jsonStr = text.slice(start, i + 1);
                try {
                    return JSON.parse(jsonStr);
                } catch (_) {
                    return null;
                }
            }
        }
    }

    return null;
}

// ─────────────────────────────────────────────
// Merge YOLO + Gemini detections
// ─────────────────────────────────────────────
function mergeDetectedObjects(yoloDetections = [], geminiObjects = []) {
    const seen = new Set();
    const result = [];
    const pushObj = (raw) => {
        if (!raw) return;
        const str = String(raw).trim();
        if (!str) return;
        const key = str.toLowerCase();
        if (seen.has(key)) return;
        seen.add(key);
        result.push(str);
    };

    if (Array.isArray(yoloDetections)) {
        for (const d of yoloDetections) {
            if (d && typeof d === 'object' && d.class) pushObj(d.class);
        }
    }
    if (Array.isArray(geminiObjects)) {
        for (const o of geminiObjects) pushObj(o);
    }
    return result.slice(0, 10);
}

// ─────────────────────────────────────────────
// Format YOLO detections
// ─────────────────────────────────────────────
function formatYoloSummary(yoloDetections = []) {
    if (!Array.isArray(yoloDetections) || yoloDetections.length === 0) return '';
    const grouped = {};
    for (const d of yoloDetections) {
        if (!d || !d.class) continue;
        const cls = String(d.class);
        if (!grouped[cls]) grouped[cls] = [];
        grouped[cls].push(Number(d.confidence || 0));
    }
    return Object.entries(grouped)
        .map(([cls, confs]) => {
            const max = Math.max(...confs).toFixed(2);
            return `${cls} (${confs.length}x, max conf ${max})`;
        })
        .join('; ');
}

// ═════════════════════════════════════════════
// 1. Analyze a problem (existing)
// ═════════════════════════════════════════════
async function analyzeProblem(
    imageBase64,
    title,
    description,
    location,
    category,
    yoloDetections = []
) {
    const parts = [];

    if (imageBase64 && imageBase64.length > 100) {
        let mimeType = 'image/jpeg';
        let rawBase64 = imageBase64;

        const m = imageBase64.match(/^data:(image\/[a-zA-Z+]+);base64,(.+)$/);
        if (m) {
            mimeType = m[1];
            rawBase64 = m[2];
        }

        parts.push({
            inlineData: { mimeType, data: rawBase64 }
        });
    }

    const yoloSummary = formatYoloSummary(yoloDetections);
    const hasYolo = yoloSummary.length > 0;

    const yoloBlock = hasYolo
        ? `YOLO object detection already ran on this image. Trusted detections:\n${yoloSummary}\n\nThese are verified detections. Do not contradict them.`
        : 'No YOLO pre-detections available. Analyze the image directly.';

    const systemInstruction = `
You are an expert community maintenance AI assistant. You analyze photos of community problems (garbage, water_leakage, flooding, road_floor_damage) and provide structured diagnostics.

ALWAYS respond with valid JSON ONLY. No markdown. JSON keys:
{
  "summary": "One sentence (max 200 chars)",
  "recommendedAction": "Concrete next step (max 300 chars)",
  "detectedObjects": ["array of specific things you see"],
  "severity": "Low" | "Medium" | "High" | "Critical",
  "estimatedHours": number (1-40),
  "confidenceScore": number (0-100)
}
`.trim();

    const userPrompt = `
Analyze this community problem:
Title: ${title || 'Untitled'}
Category: ${category || 'Other'}
Location: ${location || 'Unknown'}
Description: ${description || 'No description'}

${yoloBlock}

${imageBase64 ? 'Image attached.' : 'No image.'}

Respond with JSON only.
`.trim();

    parts.push({ text: userPrompt });

    try {
        console.log('🤖 Sending to Gemini for diagnostic...');
        const reply = await callGemini(parts, systemInstruction, 0.4);
        const parsed = extractJson(reply);

        if (!parsed) return fallbackDiagnostic(category, yoloDetections);

        const mergedObjects = mergeDetectedObjects(yoloDetections, parsed.detectedObjects || []);

        return {
            summary: String(parsed.summary || '').slice(0, 250),
            recommendedAction: String(parsed.recommendedAction || '').slice(0, 400),
            detectedObjects: mergedObjects,
            severity: ['Low', 'Medium', 'High', 'Critical'].includes(parsed.severity)
                ? parsed.severity : 'Medium',
            estimatedHours: clampNumber(parsed.estimatedHours, 1, 40, 2),
            confidenceScore: clampNumber(parsed.confidenceScore, 0, 100, 85)
        };
    } catch (error) {
        console.error('❌ Gemini analyzeProblem failed:', error.message);
        return fallbackDiagnostic(category, yoloDetections);
    }
}

// ═════════════════════════════════════════════
// 2. Draft problem fields (existing)
// ═════════════════════════════════════════════
async function draftProblemFields(imageBase64, yoloDetections = [], userHint = '') {
    const parts = [];

    if (imageBase64 && imageBase64.length > 100) {
        let mimeType = 'image/jpeg';
        let rawBase64 = imageBase64;
        const m = imageBase64.match(/^data:(image\/[a-zA-Z+]+);base64,(.+)$/);
        if (m) {
            mimeType = m[1];
            rawBase64 = m[2];
        }
        parts.push({
            inlineData: { mimeType, data: rawBase64 }
        });
    }

    const yoloSummary = formatYoloSummary(yoloDetections);
    const yoloBlock = yoloSummary
        ? `YOLO object detection found: ${yoloSummary}`
        : 'No YOLO detections.';

    const systemInstruction = `
You are Community AI. When a resident reports a community problem, you draft a professional report for them.

Look at the image, use YOLO detections, and write:
- A short, specific title (5-10 words)
- A 1-2 sentence factual description for the maintainer
- The best category from: Lighting, Plumbing, Electrical, Elevator, Security, Cleanliness, Structural, HVAC, Parking, Noise, Other
- A priority from: Low, Medium, High, Urgent
- A severity from: Low, Medium, High, Critical
- An estimated fix time in hours (1-40)

Respond with valid JSON ONLY. No markdown. Structure:
{
  "title": "...",
  "description": "...",
  "category": "...",
  "priority": "...",
  "severity": "...",
  "estimatedHours": number,
  "recommendedAction": "...",
  "confidenceScore": number
}
`.trim();

    const userPrompt = `
${yoloBlock}
${userHint ? `User hint: ${userHint}` : ''}
${imageBase64 ? 'Analyze the attached photo and draft the report.' : 'Draft based on YOLO detections.'}

Respond with JSON only.
`.trim();

    parts.push({ text: userPrompt });

    try {
        console.log('🤖 Asking Gemini to draft report fields...');
        const reply = await callGemini(parts, systemInstruction, 0.5);
        const parsed = extractJson(reply);

        if (!parsed) return fallbackDraft(yoloDetections);

        const validCategories = ['Lighting', 'Plumbing', 'Electrical', 'Elevator', 'Security', 'Cleanliness', 'Structural', 'HVAC', 'Parking', 'Noise', 'Other'];
        const validPriorities = ['Low', 'Medium', 'High', 'Urgent'];
        const validSeverities = ['Low', 'Medium', 'High', 'Critical'];

        return {
            title: String(parsed.title || '').slice(0, 120).trim(),
            description: String(parsed.description || '').slice(0, 500).trim(),
            category: validCategories.includes(parsed.category) ? parsed.category : 'Other',
            priority: validPriorities.includes(parsed.priority) ? parsed.priority : 'Medium',
            severity: validSeverities.includes(parsed.severity) ? parsed.severity : 'Medium',
            estimatedHours: clampNumber(parsed.estimatedHours, 1, 40, 2),
            recommendedAction: String(parsed.recommendedAction || '').slice(0, 300),
            confidenceScore: clampNumber(parsed.confidenceScore, 0, 100, 80),
            detectedObjects: mergeDetectedObjects(yoloDetections, [])
        };
    } catch (error) {
        console.error('❌ Gemini draftProblemFields failed:', error.message);
        return fallbackDraft(yoloDetections);
    }
}

// ═════════════════════════════════════════════
// 3. Generate AI Work Checklist for maintainer
// ═════════════════════════════════════════════
async function generateWorkChecklist(problem, yoloDetections = []) {
    const parts = [];

    if (problem.beforeImage && problem.beforeImage.length > 100) {
        let mimeType = 'image/jpeg';
        let rawBase64 = problem.beforeImage;
        const m = problem.beforeImage.match(/^data:(image\/[a-zA-Z+]+);base64,(.+)$/);
        if (m) {
            mimeType = m[1];
            rawBase64 = m[2];
        }
        parts.push({
            inlineData: { mimeType, data: rawBase64 }
        });
    }

    const yoloSummary = formatYoloSummary(yoloDetections);
    const yoloBlock = yoloSummary
        ? `YOLO detected: ${yoloSummary}`
        : 'No YOLO detections available.';

    const systemInstruction = `
You are an expert maintenance supervisor. You create clear step-by-step work checklists for community maintenance technicians.

The technician will follow your steps to fix the issue. Each step should be:
- Actionable and specific (not vague like "fix the problem")
- Include a realistic time estimate (in minutes)
- Include safety notes if relevant (e.g., "Wear gloves", "Shut off power")
- Include required materials/tools if relevant

ALWAYS respond with valid JSON ONLY. No markdown. Use this exact structure:
{
  "steps": [
    {
      "stepNumber": 1,
      "title": "Short action (5-8 words)",
      "description": "Detailed instruction for this step (1-2 sentences)",
      "estimatedMinutes": number (5-120),
      "safetyNote": "Safety warning or empty string",
      "materialsNeeded": ["item1", "item2"]
    }
  ],
  "totalEstimateHours": number,
  "safetyOverview": "One sentence summary of overall safety considerations"
}

Provide 3-6 steps. Be realistic and practical. Focus on quality work.
`.trim();

    const userPrompt = `
Create a work checklist for this community problem:

Title: ${problem.title}
Category: ${problem.category}
Location: ${problem.location}
Description: ${problem.description}
Priority: ${problem.priority}
Reporter role: ${problem.reporterRole || 'Resident'}

${yoloBlock}

${problem.beforeImage ? 'A photo of the problem is attached.' : 'No photo available.'}

Respond with JSON only.
`.trim();

    parts.push({ text: userPrompt });

    try {
        console.log('🤖 Asking Gemini for work checklist...');
        const reply = await callGemini(parts, systemInstruction, 0.5);
        const parsed = extractJson(reply);

        if (!parsed || !Array.isArray(parsed.steps)) {
            return fallbackChecklist(problem);
        }

        const steps = parsed.steps
            .filter(s => s && typeof s === 'object')
            .slice(0, 8)
            .map((s, idx) => ({
                stepNumber: clampNumber(s.stepNumber || idx + 1, 1, 100, idx + 1),
                title: String(s.title || '').slice(0, 100).trim(),
                description: String(s.description || '').slice(0, 400).trim(),
                estimatedMinutes: clampNumber(s.estimatedMinutes, 1, 480, 20),
                safetyNote: String(s.safetyNote || '').slice(0, 200).trim(),
                materialsNeeded: Array.isArray(s.materialsNeeded)
                    ? s.materialsNeeded.map(m => String(m).slice(0, 60)).slice(0, 10)
                    : []
            }));

        return {
            steps,
            totalEstimateHours: clampNumber(
                parsed.totalEstimateHours,
                0.25,
                40,
                Math.round(steps.reduce((acc, s) => acc + s.estimatedMinutes, 0) / 60)
            ),
            safetyOverview: String(parsed.safetyOverview || '').slice(0, 300).trim(),
            generatedAt: new Date()
        };

    } catch (error) {
        console.error('❌ Gemini generateWorkChecklist failed:', error.message);
        return fallbackChecklist(problem);
    }
}

// ─────────────────────────────────────────────
// 4. Chatbot
// ─────────────────────────────────────────────
async function chatReply(userMessage, history = [], userContext = {}) {
    const systemInstruction = `
You are "Community AI", a warm, concise assistant for a residential community app.

You help residents report problems, understand status, and navigate the app.

Rules:
- Be concise. 2-4 sentences unless asked for detail.
- Be warm and human.
- Never invent facts about the user's community.

User: ${userContext.name ? `Name="${userContext.name}", Role="${userContext.role}"` : 'Anonymous'}
`.trim();

    let historyText = '';
    if (history.length > 0) {
        historyText = history.map(h => `${h.role === 'user' ? 'User' : 'Assistant'}: ${h.text}`).join('\n');
    }

    const finalPrompt = `${historyText ? `Previous conversation:\n${historyText}\n\n` : ''}User: ${userMessage}\n\nAssistant:`;

    try {
        console.log('🤖 Sending chat to Gemini...');
        return (await callGemini([{ text: finalPrompt }], systemInstruction, 0.8)).trim();
    } catch (error) {
        console.error('❌ Gemini chatReply failed:', error.message);
        return "I'm having trouble connecting right now. Please try again in a moment.";
    }
}

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────
function clampNumber(value, min, max, fallback) {
    const n = Number(value);
    if (!isFinite(n)) return fallback;
    return Math.max(min, Math.min(max, Math.round(n)));
}

function fallbackDiagnostic(category, yoloDetections = []) {
    const defaults = {
        Lighting: { summary: 'Lighting issue reported.', action: 'Check bulb, ballast, circuit.' },
        Plumbing: { summary: 'Plumbing issue detected.', action: 'Inspect for leaks.' },
        Electrical: { summary: 'Electrical fault reported.', action: 'Isolate circuit safely.' },
        Elevator: { summary: 'Elevator malfunction.', action: 'Schedule technician.' },
        Security: { summary: 'Security concern reported.', action: 'Review footage.' },
        Cleanliness: { summary: 'Cleanliness issue reported.', action: 'Schedule cleanup.' },
        Structural: { summary: 'Structural damage detected.', action: 'Barricade and inspect.' }
    };
    const d = defaults[category] || { summary: 'Issue reported.', action: 'Inspect on-site.' };
    const detectedObjects = Array.isArray(yoloDetections)
        ? [...new Set(yoloDetections.map(x => x && x.class).filter(Boolean))]
        : [];

    return {
        summary: d.summary,
        recommendedAction: d.action,
        detectedObjects,
        severity: 'Medium',
        estimatedHours: 2,
        confidenceScore: 60
    };
}

function fallbackDraft(yoloDetections = []) {
    const classes = Array.isArray(yoloDetections)
        ? [...new Set(yoloDetections.map(d => d.class).filter(Boolean))]
        : [];

    const classCategoryMap = {
        garbage: 'Cleanliness',
        water_leakage: 'Plumbing',
        flooding: 'Plumbing',
        road_floor_damage: 'Structural'
    };

    const topClass = classes[0] || 'Other';
    const category = classCategoryMap[topClass] || 'Other';

    const classTitleMap = {
        garbage: 'Overflowing garbage detected',
        water_leakage: 'Water leakage detected',
        flooding: 'Flooding detected',
        road_floor_damage: 'Road or floor damage detected'
    };

    const classDescMap = {
        garbage: 'Garbage accumulation detected. Requires cleanup.',
        water_leakage: 'Water leakage detected. Plumbing inspection needed.',
        flooding: 'Flooding detected. Urgent attention recommended.',
        road_floor_damage: 'Structural damage detected. Safety inspection recommended.'
    };

    return {
        title: classTitleMap[topClass] || 'Community problem reported',
        description: classDescMap[topClass] || 'Please provide additional details.',
        category,
        priority: topClass === 'flooding' ? 'High' : 'Medium',
        severity: topClass === 'flooding' ? 'High' : 'Medium',
        estimatedHours: 2,
        recommendedAction: 'Inspect on-site and schedule repair.',
        confidenceScore: 50,
        detectedObjects: classes
    };
}

// ─────────────────────────────────────────────
// Fallback work checklist
// ─────────────────────────────────────────────
function fallbackChecklist(problem) {
    const category = problem.category || 'Other';

    const categorySteps = {
        Lighting: [
            { title: 'Inspect the fixture', description: 'Check if the bulb is blown and if the fixture has visible damage.', estimatedMinutes: 10, safetyNote: 'Ensure the light switch is off before inspection.', materialsNeeded: ['Flashlight', 'Multimeter'] },
            { title: 'Test the power circuit', description: 'Use a multimeter to confirm voltage at the fixture. Replace if no voltage.', estimatedMinutes: 15, safetyNote: 'Turn off breaker if testing continuity.', materialsNeeded: ['Multimeter', 'Insulated gloves'] },
            { title: 'Replace the bulb or fixture', description: 'Swap out the faulty bulb. If the fixture is damaged, replace it.', estimatedMinutes: 20, safetyNote: 'Wait for bulb to cool before handling.', materialsNeeded: ['Replacement bulb', 'Screwdriver'] },
            { title: 'Verify and clean up', description: 'Turn power back on and confirm the light works. Clean the area.', estimatedMinutes: 5, safetyNote: '', materialsNeeded: ['Cleaning cloth'] }
        ],
        Plumbing: [
            { title: 'Locate the leak source', description: 'Trace the water damage to its origin. Check joints, seals, and pipes.', estimatedMinutes: 15, safetyNote: 'Shut off water supply at the main valve first.', materialsNeeded: ['Flashlight', 'Towel'] },
            { title: 'Apply temporary fix', description: 'Use plumber\'s tape or a patch to stop immediate flow.', estimatedMinutes: 15, safetyNote: 'Wear gloves to avoid contamination.', materialsNeeded: ['Plumber\'s tape', 'Gloves'] },
            { title: 'Replace damaged component', description: 'Swap out the worn seal, pipe segment, or fitting.', estimatedMinutes: 30, safetyNote: 'Drain water from pipes before cutting.', materialsNeeded: ['Replacement fitting', 'Pipe wrench'] },
            { title: 'Test for leaks', description: 'Turn water back on and verify no leaks. Dry the area thoroughly.', estimatedMinutes: 10, safetyNote: '', materialsNeeded: ['Towel', 'Bucket'] }
        ],
        Cleanliness: [
            { title: 'Assess the area', description: 'Evaluate the extent of garbage or waste. Take note of any hazards.', estimatedMinutes: 5, safetyNote: 'Wear gloves and mask.', materialsNeeded: ['Gloves', 'Mask'] },
            { title: 'Collect and dispose', description: 'Bag all waste. Use separate bags for recyclables.', estimatedMinutes: 30, safetyNote: 'Watch for sharp items.', materialsNeeded: ['Trash bags', 'Broom'] },
            { title: 'Sanitize the area', description: 'Disinfect the affected area. Ensure no residue remains.', estimatedMinutes: 15, safetyNote: '', materialsNeeded: ['Disinfectant', 'Mop'] },
            { title: 'Final inspection', description: 'Confirm the area is clean and take an after-photo.', estimatedMinutes: 5, safetyNote: '', materialsNeeded: ['Camera'] }
        ],
        Structural: [
            { title: 'Secure the area', description: 'Place warning cones or tape to prevent access.', estimatedMinutes: 10, safetyNote: 'Prioritize pedestrian safety.', materialsNeeded: ['Warning tape', 'Cones'] },
            { title: 'Assess the damage', description: 'Document cracks, holes, or damage with photos.', estimatedMinutes: 15, safetyNote: '', materialsNeeded: ['Camera', 'Measuring tape'] },
            { title: 'Apply temporary repair', description: 'Use quick-set material to stabilize the issue.', estimatedMinutes: 30, safetyNote: 'Follow material safety guidelines.', materialsNeeded: ['Quick-set cement', 'Trowel'] },
            { title: 'Final verification', description: 'Confirm stability. Take after-photo. Report to admin for permanent fix.', estimatedMinutes: 10, safetyNote: '', materialsNeeded: ['Camera'] }
        ]
    };

    const steps = (categorySteps[category] || [
        { title: 'Inspect the problem', description: 'Assess the issue on-site.', estimatedMinutes: 15, safetyNote: 'Take safety precautions.', materialsNeeded: [] },
        { title: 'Apply fix', description: 'Repair the problem using appropriate tools.', estimatedMinutes: 30, safetyNote: '', materialsNeeded: [] },
        { title: 'Verify and clean up', description: 'Confirm the fix and clean the area. Take an after-photo.', estimatedMinutes: 10, safetyNote: '', materialsNeeded: ['Camera'] }
    ]).map((s, i) => ({ stepNumber: i + 1, ...s }));

    const totalMinutes = steps.reduce((acc, s) => acc + s.estimatedMinutes, 0);

    return {
        steps,
        totalEstimateHours: Math.round((totalMinutes / 60) * 10) / 10,
        safetyOverview: 'Follow general safety guidelines. Wear appropriate protective equipment.',
        generatedAt: new Date()
    };
}

module.exports = {
    analyzeProblem,
    draftProblemFields,
    generateWorkChecklist,
    chatReply,
    isGeminiConfigured
};
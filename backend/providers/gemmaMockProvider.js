/**
 * Gemma-backed mock of the YouCam APIs.
 *
 * Exposes:
 *   createTask(input) -> { taskId, status }
 *   getTask(taskId)   -> { taskId, status, result?, error? }
 *
 * Work starts at create time and runs in the background, so polling is genuinely
 * polling — the app exercises the same async path it will use against real YouCam,
 * for free, before a single credit is spent.
 */

const { randomUUID } = require("node:crypto");

const tasks = new Map();

const OLLAMA_URL = process.env.OLLAMA_URL || "http://localhost:11434/api/chat";
const GEMMA_MODEL = process.env.GEMMA_MODEL || "gemma4:cloud";

async function askGemma(prompt) {
    const ollamaResponse = await fetch(OLLAMA_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            model: GEMMA_MODEL,
            stream: false,
            messages: [{ role: "user", content: prompt }]
        })
    });

    if (!ollamaResponse.ok) {
        throw new Error(`Ollama returned ${ollamaResponse.status}`);
    }

    const data = await ollamaResponse.json();
    return data.message.content;
}

// ---- task lifecycle ----

async function createTask(input) {
    const taskId = randomUUID();

    tasks.set(taskId, {
        taskId,
        status: "processing",
        createdAt: new Date().toISOString()
    });

    // Deliberately not awaited. The HTTP response returns immediately with 202,
    // exactly like the real API, and the app polls for the outcome.
    run(taskId, input).catch((error) => {
        console.error(`task ${taskId} failed:`, error.message);
        tasks.set(taskId, {
            taskId,
            status: "failed",
            error: error.message
        });
    });

    return { taskId, status: "processing" };
}

async function getTask(taskId) {
    const task = tasks.get(taskId);

    if (!task) {
        throw new Error(`Task not found: ${taskId}`);
    }

    return task;
}

async function run(taskId, input) {
    const result = input.kind === "SKIN_ANALYSIS"
        ? await runSkinAnalysis(input)
        : await runTryOn(input);

    tasks.set(taskId, {
        taskId,
        status: "success",
        completedAt: new Date().toISOString(),
        result
    });
}

// ---- TRY_ON ----

async function runTryOn(input) {
    const note = await askGemma(
        `You are standing in for a virtual try-on API.
         A person is trying on one garment, target area: ${input.renderTarget || "AUTO"}.
         Reply with ONE short sentence describing how the result looks on them.
         No preamble, no markdown, no quotes.`
    );

    return {
        // The mock produces no image. The app renders the garment tile and shows this
        // note instead, which is enough to prove the whole path works end to end.
        imageUrl: null,
        note: note.trim()
    };
}

// ---- SKIN_ANALYSIS ----

const SKIN_PROMPT = `You are standing in for a skin analysis API.
Invent one plausible reading for a real human face.
Reply with ONLY this JSON object, no markdown fence, no commentary:
{"undertone":"WARM|COOL|NEUTRAL","fitzpatrick":1-6,"redness":0.0-1.0,"dullness":0.0-1.0,"darkCircles":0.0-1.0}`;

async function runSkinAnalysis() {
    let skin;

    try {
        const raw = await askGemma(SKIN_PROMPT);
        skin = parseSkinJson(raw);
    } catch (error) {
        // A mock that dies because a language model felt chatty is a bad mock.
        console.warn(`skin analysis fell back to defaults: ${error.message}`);
        skin = fallbackSkin();
    }

    return {
        skin,
        note: "Simulated reading — Gemma mock, no YouCam credits used."
    };
}

function parseSkinJson(raw) {
    // Models like to wrap JSON in prose or a code fence. Take the first {...} block.
    const match = raw.match(/\{[\s\S]*\}/);
    if (!match) {
        throw new Error("no JSON object in model reply");
    }

    const parsed = JSON.parse(match[0]);

    const undertone = String(parsed.undertone || "NEUTRAL").toUpperCase();

    return {
        undertone: ["WARM", "COOL", "NEUTRAL"].includes(undertone) ? undertone : "NEUTRAL",
        fitzpatrick: clampInt(parsed.fitzpatrick, 1, 6, 3),
        redness: clampFloat(parsed.redness),
        dullness: clampFloat(parsed.dullness),
        darkCircles: clampFloat(parsed.darkCircles)
    };
}

function fallbackSkin() {
    // Varies between calls so you can watch the recommendation actually change.
    return {
        undertone: ["WARM", "COOL", "NEUTRAL"][Math.floor(Math.random() * 3)],
        fitzpatrick: 3 + Math.floor(Math.random() * 2),
        redness: round2(Math.random()),
        dullness: round2(Math.random()),
        darkCircles: round2(Math.random())
    };
}

function clampInt(value, min, max, fallback) {
    const n = Number.parseInt(value, 10);
    return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
}

function clampFloat(value) {
    const n = Number.parseFloat(value);
    return Number.isFinite(n) ? round2(Math.min(1, Math.max(0, n))) : 0;
}

function round2(n) {
    return Math.round(n * 100) / 100;
}

module.exports = {
    askGemma,
    createTask,
    getTask
};

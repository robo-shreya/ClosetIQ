/**
 * Real PerfectCorp YouCam provider.
 *
 * Exposes exactly the interface providers/index.js expects:
 *   createTask(input) -> { taskId, status }
 *   getTask(taskId)   -> { taskId, status, result?, error? }
 *
 * A local Gemma mock implemented this same shape during early development, so the whole
 * async pipeline — upload, poll, backoff, error handling — was proven before this file
 * ever spent a real credit. It has been removed now that its job is done; every quirk of
 * YouCam's own response format is absorbed here, so the Android app cannot tell either
 * provider was ever there.
 *
 * ---------------------------------------------------------------------------
 * The API key lives here and only here. An APK is a zip file — anything compiled
 * into it can be read out of a release build in about a minute.
 * ---------------------------------------------------------------------------
 */

const { uploadImage, createTask: createYouCamTask, pollTask } = require("./youCamClient");
const { analyseSkinColor } = require("./skinTone");

/** Our task id -> the YouCam task(s) behind it. */
const TASKS = new Map();

const FEATURE_SKIN = "skin-analysis";
const FEATURE_SKIN_TONE = "skin-tone-analysis";
const FEATURE_CLOTH = "cloth";

/**
 * The three concerns our scoring engine needs, using YouCam's exact names.
 *
 * Verified against the live API — a wrong name returns
 * `{"error":"<index> is not one of the accepted values."}` where the number is the
 * INDEX into dst_actions, which is how these were pinned down. Notably it is
 * `dark_circle_v2`, not `dark_circle`. The full accepted set is:
 *
 *   wrinkle, pore, texture, acne, redness, oiliness, moisture, radiance,
 *   dark_circle_v2, eye_bag, firmness, droopy_upper_eyelid, droopy_lower_eyelid,
 *   age_spot, hd_redness, hd_dark_circle
 *
 * Do not add the hd_* variants here: SD and HD concerns cannot be mixed in one request.
 */
const SKIN_ACTIONS = ["redness", "radiance", "dark_circle_v2"];

/**
 * YouCam's ui_score runs 0-100 where HIGHER MEANS HEALTHIER SKIN. Our domain layer
 * wants the opposite: 0..1 where higher means MORE of the concern. Inverting here is
 * the single most important line in this file — get it backwards and every clothing
 * recommendation silently inverts while still looking plausible.
 *
 * Set YOUCAM_INVERT_SKIN_SCORES=0 in .env if a probe run shows the direction differs.
 */
const INVERT_SCORES = process.env.YOUCAM_INVERT_SKIN_SCORES !== "0";

function concernToUnit(uiScore) {
    if (!Number.isFinite(uiScore)) return 0;
    const normalised = Math.min(100, Math.max(0, uiScore)) / 100;
    return round2(INVERT_SCORES ? 1 - normalised : normalised);
}

// ---- create ----

async function createTask(input) {
    const taskId = `yc-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

    if (input.kind === "SKIN_ANALYSIS") {
        const entry = await createSkinTasks(input);
        TASKS.set(taskId, entry);
        return { taskId, status: "processing" };
    }

    if (input.kind === "TRY_ON") {
        const entry = await createTryOnTask(input);
        TASKS.set(taskId, entry);
        return { taskId, status: "processing" };
    }

    throw new Error(`Unknown task kind: ${input.kind}`);
}

/**
 * One selfie, two YouCam tasks: Skin Analysis for today's condition and Skin Tone
 * Analysis for the stable undertone. The image is uploaded once per feature because
 * YouCam scopes file ids to a feature, but the user only ever took one photo.
 */
async function createSkinTasks(input) {
    if (!input.personImage) {
        throw new Error("SKIN_ANALYSIS needs personImage");
    }

    const [analysisFileId, toneFileId] = await Promise.all([
        uploadImage(FEATURE_SKIN, input.personImage),
        uploadImage(FEATURE_SKIN_TONE, input.personImage)
    ]);

    const [analysisTaskId, toneTaskId] = await Promise.all([
        createYouCamTask(FEATURE_SKIN, {
            src_file_id: analysisFileId,
            dst_actions: SKIN_ACTIONS,
            format: "json"
        }),
        createYouCamTask(FEATURE_SKIN_TONE, {
            src_file_id: toneFileId
        })
    ]);

    return {
        kind: "SKIN_ANALYSIS",
        parts: [
            { feature: FEATURE_SKIN, id: analysisTaskId },
            { feature: FEATURE_SKIN_TONE, id: toneTaskId }
        ]
    };
}

async function createTryOnTask(input) {
    if (!input.personImage || !input.garmentImage) {
        throw new Error("TRY_ON needs both personImage and garmentImage");
    }

    const [personFileId, garmentFileId] = await Promise.all([
        uploadImage(FEATURE_CLOTH, input.personImage),
        uploadImage(FEATURE_CLOTH, input.garmentImage)
    ]);

    return {
        kind: "TRY_ON",
        parts: [
            {
                feature: FEATURE_CLOTH,
                id: await createYouCamTask(FEATURE_CLOTH, {
                    src_file_id: personFileId,
                    ref_file_id: garmentFileId,
                    garment_category: mapRenderTarget(input.renderTarget)
                })
            }
        ]
    };
}

/** Our RenderTarget enum -> YouCam's garment_category. */
function mapRenderTarget(target) {
    switch (String(target || "").toUpperCase()) {
        case "UPPER_BODY": return "upper_body";
        case "LOWER_BODY": return "lower_body";
        case "FULL_BODY": return "full_body";
        case "SHOES": return "shoes";
        default: return "auto";
    }
}

// ---- poll ----

async function getTask(taskId) {
    const entry = TASKS.get(taskId);
    if (!entry) {
        throw new Error(`Task not found: ${taskId}`);
    }

    const polled = await Promise.all(
        entry.parts.map((part) => pollTask(part.feature, part.id).then((r) => ({ part, ...r })))
    );

    const failed = polled.find((p) => p.failed);
    if (failed) {
        return {
            taskId,
            status: "failed",
            error: `${failed.part.feature} failed: ${failed.data.error || failed.status}`
        };
    }

    if (!polled.every((p) => p.done)) {
        return { taskId, status: "processing" };
    }

    const byFeature = Object.fromEntries(polled.map((p) => [p.part.feature, p.data]));

    return {
        taskId,
        status: "success",
        result: entry.kind === "SKIN_ANALYSIS"
            ? normaliseSkin(byFeature)
            : normaliseTryOn(byFeature[FEATURE_CLOTH])
    };
}

// ---- normalisation ----

function normaliseSkin(byFeature) {
    const outputs = byFeature[FEATURE_SKIN]?.results?.output || [];
    const scoreOf = (type) =>
        outputs.find((o) => String(o.type).toLowerCase() === type)?.ui_score;

    // "Dullness" is not a YouCam concern. Radiance is its inverse, and texture is the
    // stand-in if an account does not have radiance enabled.
    const dullnessSource = scoreOf("radiance") ?? scoreOf("texture");

    const tone = byFeature[FEATURE_SKIN_TONE]?.results || {};

    // Verified against the live API: skin-tone-analysis returns hex colours, NOT an
    // undertone or a Fitzpatrick type. Both are derived from skin_color — see skinTone.js.
    const derived = analyseSkinColor(tone.color?.skin_color);

    if (tone.face_quality && tone.face_quality.has_face === false) {
        throw new Error("No face detected in the photo");
    }

    return {
        skin: {
            undertone: derived?.undertone || "NEUTRAL",
            fitzpatrick: derived?.fitzpatrick || 3,
            redness: concernToUnit(scoreOf("redness")),
            dullness: concernToUnit(dullnessSource),
            darkCircles: concernToUnit(scoreOf("dark_circle_v2") ?? scoreOf("dark_circle"))
        },
        note: "PerfectCorp YouCam — Skin Analysis + Skin Tone Analysis",
        // Kept for debugging and for the eventual richer palette: the measured colours
        // are strictly more information than the enum we reduce them to.
        colors: tone.color || null,
        derived: derived ? { ita: derived.ita, hue: derived.hue, lab: derived.lab } : null
    };
}

function normaliseTryOn(data) {
    const results = data?.results || {};

    // Verified against the live API: cloth puts the render at results.url, NOT under
    // results.output the way skin-analysis does. Both are checked, plus the array and
    // object variants, because the two features genuinely disagree on shape.
    const imageUrl =
        pickUrl(results.url) ||
        pickUrl(results.output) ||
        pickUrl(results.dst_url) ||
        pickUrl(results.image_url);

    if (imageUrl) {
        return { imageUrl, note: null };
    }

    // Verified behaviour: a cloth task with a valid request but an unusable photo
    // completes with task_status "success" and an EMPTY results object rather than an
    // error. Saying so plainly beats a silent blank screen in the app.
    const empty = Object.keys(results).length === 0;

    return {
        imageUrl: null,
        note: empty
            ? "Try-on produced no image — YouCam could not use these photos. " +
              "The person shot needs a clear, well-lit half or full body, short side 480px or more."
            // Dump the actual values, not just the keys. A key that exists with a null
            // value and a key we failed to parse look identical otherwise, and telling
            // them apart is worth more than a tidy message.
            : `Try-on returned no image url. Raw results: ${JSON.stringify(results).slice(0, 400)}`
    };
}

/** A url can arrive as a string, an array of strings, or an object wrapping one. */
function pickUrl(value) {
    if (!value) return null;
    if (typeof value === "string") return value;
    if (Array.isArray(value)) return pickUrl(value[0]);
    return value.url || value.image_url || value.dst_url || null;
}

function round2(n) {
    return Math.round(n * 100) / 100;
}

module.exports = {
    createTask,
    getTask,
    // exported for backend/probe.js
    normaliseSkin,
    normaliseTryOn
};

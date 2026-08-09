/**
 * Real PerfectCorp YouCam provider.
 *
 * Exposes the same two functions as the Gemma mock:
 *   createTask(input) -> { taskId, status }
 *   getTask(taskId)   -> { taskId, status, result?, error? }
 *
 * Switching to this is a one-line change in backend/.env:
 *   PROVIDER=youcam
 *
 * Nothing in the Android app changes. That is the entire point of the split.
 *
 * ------------------------------------------------------------------------------
 * WHY THE CREDENTIALS LIVE HERE AND NOT IN THE APK
 *
 * An APK is a zip file. Anything compiled into it — including a key you passed
 * through BuildConfig — can be read out of a release build in about a minute. This
 * file is the only place the YouCam client id and secret ever exist.
 * ------------------------------------------------------------------------------
 */

const TASKS = new Map();

const CLIENT_ID = process.env.YOUCAM_CLIENT_ID;
const SECRET_KEY = process.env.YOUCAM_SECRET_KEY;

let cachedToken = null;
let tokenExpiresAt = 0;

/**
 * TODO(when you switch on YouCam): exchange client id + secret for an access token.
 *
 * PerfectCorp's flow is roughly: build an id_token by encrypting a timestamped payload
 * with your RSA secret key, POST it to the auth endpoint, and get back an access token
 * with a limited lifetime. Cache it here and reuse it until [tokenExpiresAt].
 *
 * Check the current shape against https://docs.perfectcorp.com/develop/introduction
 * before writing this — the auth handshake is the part most likely to have changed.
 */
async function getAccessToken() {
    if (cachedToken && Date.now() < tokenExpiresAt) {
        return cachedToken;
    }

    if (!CLIENT_ID || !SECRET_KEY) {
        throw new Error(
            "YOUCAM_CLIENT_ID / YOUCAM_SECRET_KEY missing from backend/.env"
        );
    }

    throw new Error("YouCam auth handshake not implemented yet");
}

/**
 * TODO: create the right YouCam task for input.kind.
 *
 *   TRY_ON         → upload person + garment images, start a Clothes VTO task with
 *                    input.renderTarget mapped to YouCam's body-part parameter
 *                    (AUTO / FULL_BODY / UPPER_BODY / LOWER_BODY / SHOES).
 *
 *   SKIN_ANALYSIS  → upload the selfie ONCE, then start both AI Skin Analysis and
 *                    AI Facial Color Tones against it. Two calls, one image — the user
 *                    took one photo and must never be asked for a second.
 *
 * Return { taskId, status: "processing" }. If a kind needs several YouCam tasks, mint
 * your own id here and keep the YouCam ids in TASKS against it, so the app still sees
 * exactly one task. Images arrive as { base64, mimeType } and are already inside the
 * upload envelope — the app resizes before sending.
 */
async function createTask(input) {
    await getAccessToken();
    throw new Error(`YouCam createTask not implemented (kind: ${input.kind})`);
}

/**
 * TODO: poll YouCam for this task and normalise the reply to the shared shape.
 *
 * Return exactly what the mock returns, so the app cannot tell the difference:
 *
 *   { taskId, status: "processing" }
 *   { taskId, status: "success", result: { imageUrl, note } }                  // TRY_ON
 *   { taskId, status: "success", result: { skin: { undertone, fitzpatrick,     // SKIN
 *                                                  redness, dullness, darkCircles } } }
 *   { taskId, status: "failed", error: "..." }
 *
 * Normalising here rather than in the app is what keeps the swap free: every mapping
 * quirk in YouCam's response format is absorbed by this file.
 *
 * Skin scores come back on YouCam's own scales. Convert them to 0..1 with higher meaning
 * *more* of the concern — SkinStateModifier assumes that direction, and getting it
 * backwards will silently invert every recommendation.
 */
async function getTask(taskId) {
    throw new Error(`YouCam getTask not implemented (task: ${taskId})`);
}

module.exports = {
    createTask,
    getTask
};

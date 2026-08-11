/**
 * Thin client for the PerfectCorp YouCam V2 API.
 *
 * V2 needs no auth handshake — every request carries `Authorization: Bearer <api key>`.
 * (V1 required an RSA-signed id_token exchange; we are deliberately not using it.)
 *
 * Every capability follows the same three steps:
 *   1. POST /s2s/v2.0/file/{feature}       -> file_id + a pre-signed PUT url
 *   2. PUT  <pre-signed url>               -> the raw image bytes
 *   3. POST /s2s/v2.0/task/{feature}       -> task_id
 *   4. GET  /s2s/v2.0/task/{feature}/{id}  -> poll until task_status is terminal
 */

const BASE_URL = process.env.YOUCAM_BASE_URL || "https://yce-api-01.makeupar.com";

const DEBUG = process.env.YOUCAM_DEBUG === "1";

function apiKey() {
    const key = process.env.YOUCAM_API_KEY;
    if (!key) {
        throw new Error(
            "YOUCAM_API_KEY missing from backend/.env — get one at " +
            "https://yce.makeupar.com/api-console/en/api-keys/"
        );
    }
    return key;
}

function log(label, payload) {
    if (!DEBUG) return;
    console.log(`[youcam] ${label}:`, JSON.stringify(payload, null, 2).slice(0, 2000));
}

async function call(path, { method = "GET", body } = {}) {
    const response = await fetch(`${BASE_URL}${path}`, {
        method,
        headers: {
            Authorization: `Bearer ${apiKey()}`,
            "Content-Type": "application/json"
        },
        body: body ? JSON.stringify(body) : undefined
    });

    const text = await response.text();

    if (!response.ok) {
        throw new Error(`YouCam ${method} ${path} -> ${response.status}: ${text.slice(0, 500)}`);
    }

    let json;
    try {
        json = JSON.parse(text);
    } catch {
        throw new Error(`YouCam ${path} returned non-JSON: ${text.slice(0, 200)}`);
    }

    log(`${method} ${path}`, json);
    return json;
}

/**
 * Upload one image and return its file_id.
 *
 * [image] is { base64, mimeType } exactly as the Android app sends it.
 */
async function uploadImage(feature, image) {
    const buffer = Buffer.from(image.base64, "base64");
    const contentType = image.mimeType || "image/jpeg";
    const extension = contentType.includes("png") ? "png" : "jpg";

    const initiated = await call(`/s2s/v2.0/file/${feature}`, {
        method: "POST",
        body: {
            files: [
                {
                    content_type: contentType,
                    file_name: `closetiq-${Date.now()}.${extension}`,
                    file_size: buffer.length
                }
            ]
        }
    });

    const file = initiated?.data?.files?.[0];
    if (!file?.file_id) {
        throw new Error(`Upload init for ${feature} returned no file_id`);
    }

    // The pre-signed PUT goes straight to storage, so it carries no Bearer header.
    const request = file.requests?.[0];
    if (!request?.url) {
        throw new Error(`Upload init for ${feature} returned no pre-signed url`);
    }

    const put = await fetch(request.url, {
        method: request.method || "PUT",
        headers: request.headers || { "Content-Type": contentType },
        body: buffer
    });

    if (!put.ok) {
        const detail = await put.text();
        throw new Error(`Pre-signed upload failed (${put.status}): ${detail.slice(0, 300)}`);
    }

    return file.file_id;
}

async function createTask(feature, body) {
    const created = await call(`/s2s/v2.0/task/${feature}`, { method: "POST", body });

    const taskId = created?.data?.task_id;
    if (!taskId) {
        throw new Error(`Task creation for ${feature} returned no task_id`);
    }

    return taskId;
}

/** One poll. Returns { done, failed, status, data }. */
async function pollTask(feature, taskId) {
    const polled = await call(`/s2s/v2.0/task/${feature}/${taskId}`);
    const data = polled?.data || {};
    const status = String(data.task_status || "").toLowerCase();

    return {
        status,
        data,
        done: status === "success",
        failed: status === "error" || status === "failed"
    };
}

module.exports = {
    BASE_URL,
    uploadImage,
    createTask,
    pollTask,
    call
};

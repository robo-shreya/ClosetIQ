const youCamProvider = require("./youCamProvider");

/**
 * The provider exposes exactly:
 *   createTask(input) -> { taskId, status }
 *   getTask(taskId)   -> { taskId, status, result?, error? }
 *
 * That shape used to be shared with a local Gemma mock — the same interface implemented
 * against a free model, so the whole async pipeline (upload, poll, backoff, error
 * handling) could be built and proven before spending a real credit. The mock is gone
 * now that the app is past that stage; this indirection is what let removing it be a
 * one-file change instead of a rewrite.
 *
 * PROVIDER is the current name; TRYON_PROVIDER is still read so an older .env works.
 */
function providerName() {
    return (process.env.PROVIDER || process.env.TRYON_PROVIDER || "youcam").toLowerCase();
}

function getProvider() {
    const name = providerName();

    if (name !== "youcam") {
        throw new Error(`Unknown PROVIDER: ${name}. Only "youcam" is supported.`);
    }

    return youCamProvider;
}

module.exports = {
    getProvider,
    providerName
};

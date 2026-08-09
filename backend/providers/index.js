const gemmaMockProvider = require("./gemmaMockProvider");
const youCamProvider = require("./youCamProvider");

/**
 * Both providers expose exactly:
 *   createTask(input) -> { taskId, status }
 *   getTask(taskId)   -> { taskId, status, result?, error? }
 *
 * Keeping the shapes identical is what makes the swap a one-line env change.
 * PROVIDER is the current name; TRYON_PROVIDER is still read so an older .env works.
 */
function providerName() {
    return (process.env.PROVIDER || process.env.TRYON_PROVIDER || "gemma").toLowerCase();
}

function getProvider() {
    const name = providerName();

    if (name === "gemma") {
        return gemmaMockProvider;
    }

    if (name === "youcam") {
        return youCamProvider;
    }

    throw new Error(
        `Unknown PROVIDER: ${name}. Use "gemma" or "youcam".`
    );
}

module.exports = {
    getProvider,
    providerName
};

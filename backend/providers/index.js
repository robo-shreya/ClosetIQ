const gemmaMockProvider = require("./gemmaMockProvider");
const youCamProvider = require("./youCamProvider");

function getTryOnProvider() {
    const providerName = process.env.TRYON_PROVIDER;

    if (providerName === "gemma") {
        return gemmaMockProvider;
    }

    if (providerName === "youcam") {
        return youCamProvider;
    }

    throw new Error(
        `Unknown TRYON_PROVIDER: ${providerName}`
    );
}

module.exports = {
    getTryOnProvider
};
process.loadEnvFile();

const express = require("express");
const { getProvider, providerName } = require("./providers");

const app = express();
const PORT = process.env.PORT || 3000;

const provider = getProvider();

console.log(`ClosetIQ backend — provider: ${providerName()}`);

// Base64 images travel in the JSON body. 25mb covers a 1600px JPEG comfortably.
app.use(express.json({ limit: "25mb" }));

app.get("/health", (request, response) => {
    response.json({
        status: "ok",
        service: "ClosetIQ backend",
        provider: providerName()
    });
});

/**
 * One task endpoint for both YouCam capabilities, because both are async in exactly the
 * same way. The app has one poller instead of two, and swapping Gemma for YouCam is a
 * change to backend/.env — nothing in the Android app moves.
 *
 * Body: { kind: "TRY_ON" | "SKIN_ANALYSIS", personImage?, garmentImage?, renderTarget? }
 */
app.post("/api/tasks", async (request, response) => {
    const { kind } = request.body || {};

    if (kind !== "TRY_ON" && kind !== "SKIN_ANALYSIS") {
        return response.status(400).json({
            status: "error",
            message: `Unknown task kind: ${kind}`
        });
    }

    try {
        const task = await provider.createTask(request.body);
        response.status(202).json(task);
    } catch (error) {
        console.error("createTask failed:", error);
        response.status(500).json({ status: "error", message: error.message });
    }
});

app.get("/api/tasks/:taskId", async (request, response) => {
    try {
        const task = await provider.getTask(request.params.taskId);
        response.json(task);
    } catch (error) {
        response.status(404).json({ status: "error", message: error.message });
    }
});

app.listen(PORT, "0.0.0.0", () => {
    console.log(`Listening on http://localhost:${PORT}`);
    console.log(`The Android emulator reaches this at http://10.0.2.2:${PORT}`);
});

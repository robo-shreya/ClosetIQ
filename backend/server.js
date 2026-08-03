process.loadEnvFile();

const express = require("express");

console.log(`Try-on provider: ${process.env.TRYON_PROVIDER}`);

const app = express();
const PORT = 3000;

const { getTryOnProvider } = require("./providers");

const tryOnProvider = getTryOnProvider();

app.use(express.json());

app.get("/health", (request, response) => {
    response.json({
        status: "ok",
        service: "ClosetIQ mock backend"
    });
});

app.post("/api/try-on/tasks", async (request, response) => {
    try {
        const task = await tryOnProvider.createTryOnTask(
            request.body
        );

        response.status(202).json(task);
    } catch (error) {
        response.status(500).json({
            status: "error",
            message: error.message
        });
    }
});

app.get("/api/try-on/tasks/:taskId", async (request, response) => {
    try {
        const task = await tryOnProvider.getTryOnTask(
            request.params.taskId
        );

        response.json(task);
    } catch (error) {
        response.status(404).json({
            status: "error",
            message: error.message
        });
    }
});

app.listen(PORT, "0.0.0.0", () => {
    console.log(`Backend running at http://localhost:${PORT}`);
});
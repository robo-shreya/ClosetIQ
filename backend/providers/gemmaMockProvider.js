//Both providers should expose:
// createTryOnTask(input)
//  getTryOnTask(taskId)

const { randomUUID } = require("node:crypto");

const tasks = new Map();

async function askGemma(prompt) {
    const ollamaResponse = await fetch(
        "http://localhost:11434/api/chat",
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                model: "gemma4:cloud",
                stream: false,
                messages: [
                    {
                        role: "user",
                        content: prompt
                    }
                ]
            })
        }
    );

    if (!ollamaResponse.ok) {
        throw new Error(`Ollama returned ${ollamaResponse.status}`);
    }

    const data = await ollamaResponse.json();
    return data.message.content;
}

async function createTryOnTask(input) {
    const taskId = randomUUID();

    const task = {
        taskId,
        status: "processing",
        input,
        createdAt: new Date().toISOString()
    };

    tasks.set(taskId, task);

    return {
        taskId: task.taskId,
        status: task.status
    };
}

async function getTryOnTask(taskId) {
    const task = tasks.get(taskId);

    if (!task) {
        throw new Error(`Task not found: ${taskId}`);
    }

    if (task.status === "processing") {
        const message = await askGemma(`
            You are simulating a successful YouCam virtual try-on task.
            Input: ${JSON.stringify(task.input)}
            Reply with one short sentence describing the successful result.
        `);

        task.status = "success";
        task.completedAt = new Date().toISOString();
        task.result = {
            imageUrl: "https://example.com/mock-try-on-result.jpg",
            message: message.trim()
        };
    }

    return task;
}

module.exports = {
    askGemma,
    createTryOnTask,
    getTryOnTask
};